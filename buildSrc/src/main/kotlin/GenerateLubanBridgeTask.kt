import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

abstract class GenerateLubanBridgeTask : DefaultTask() {
    @get:InputDirectory
    abstract val generatedJavaDir: DirectoryProperty

    @get:InputDirectory
    abstract val generatedDataDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val javaDir = generatedJavaDir.get().asFile
        val dataDir = generatedDataDir.get().asFile
        val outDir = outputDir.get().asFile.apply { mkdirs() }

        val gameTablesGen = javaDir.walkTopDown()
            .firstOrNull { it.isFile && it.name == "GameTablesGen.java" }
            ?: error("generated Luban tables entry not found under $javaDir")

        val tableEntries = parseTableEntries(gameTablesGen.readText())
        require(tableEntries.isNotEmpty()) { "no tables found in $gameTablesGen" }

        val artifactFiles = dataDir.listFiles()
            ?.filter { it.isFile && it.extension == "bytes" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        require(artifactFiles.isNotEmpty()) { "no Luban binary artifacts found in $dataDir" }

        writeGeneratedFile(
            File(outDir, "GeneratedGameTables.kt"),
            renderGeneratedGameTables(),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedGameTableTypes.kt"),
            renderGeneratedGameTableTypes(tableEntries),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedGameTableAdapters.kt"),
            renderGeneratedGameTableAdapters(tableEntries),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedGameTablesSnapshotBridge.kt"),
            renderGeneratedGameTablesSnapshotBridge(tableEntries),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedGameTableAccessors.kt"),
            renderGeneratedGameTableAccessors(tableEntries),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedLubanMetadata.kt"),
            renderGeneratedLubanMetadata(artifactFiles),
        )
    }

    private fun parseTableEntries(source: String): List<TableEntry> {
        val lineRegex = Regex("""_(\w+)\s*=\s*new\s+([\w.]+)\(loader\.load\("([^"]+)"\)\);""")
        return lineRegex.findAll(source).map { match ->
            val tableClassFqcn = match.groupValues[2]
            val tableClassSimple = tableClassFqcn.substringAfterLast('.')
            val tableFile = File(generatedJavaDir.get().asFile, tableClassFqcn.replace('.', '/') + ".java")
            require(tableFile.isFile) { "generated Luban table source not found: $tableFile" }
            val tableSource = tableFile.readText()
            val shape = parseTableShape(tableSource, tableFile)
            val rowFqcn = shape.rowFqcn
            val rowSimple = rowFqcn.substringAfterLast('.')
            val baseName = snakeToCamel(rowSimple)
            TableEntry(
                delegatePropertyName = decapitalize(tableClassSimple),
                tableClassFqcn = tableClassFqcn,
                rowFqcn = rowFqcn,
                rowAlias = "${baseName}Row",
                adapterClass = "Tb$baseName",
                tableName = pluralize(camelToSnake(baseName)),
                tableProperty = "tb$baseName",
                shape = shape,
            )
        }.toList()
    }

    private fun parseTableShape(tableSource: String, tableFile: File): TableShape {
        val mapRegex = Regex("""HashMap<([^,>]+),\s*([^>]+)>\s+_dataMap""")
        val mapMatch = mapRegex.find(tableSource)
        if (mapMatch != null) {
            val keyFieldRegex = Regex("""_dataMap\.put\(_v\.([A-Za-z0-9_]+),\s*_v\);""")
            val keyFieldMatch = requireNotNull(keyFieldRegex.find(tableSource)) {
                "unable to parse key field in $tableFile"
            }
            return TableShape.Keyed(
                keyType = toKotlinType(mapMatch.groupValues[1].trim()),
                rowFqcn = mapMatch.groupValues[2].trim(),
                keyField = keyFieldMatch.groupValues[1],
            )
        }

        val listRegex = Regex("""ArrayList<([^>]+)>\s+_dataList""")
        val listMatch = listRegex.find(tableSource)
        if (listMatch != null) {
            return TableShape.ListLike(
                rowFqcn = listMatch.groupValues[1].trim(),
            )
        }

        val singletonRegex = Regex("""private\s+final\s+([\w.]+)\s+_data;""")
        val singletonMatch = singletonRegex.find(tableSource)
        if (singletonMatch != null) {
            return TableShape.Singleton(
                rowFqcn = singletonMatch.groupValues[1].trim(),
            )
        }

        error("unsupported Luban table shape in $tableFile")
    }

    private fun renderGeneratedGameTables(): String {
        return """
            |package com.mikai233.common.config.luban
            |
            |import com.mikai233.common.config.luban.gen.GameTablesGen
            |import luban.ByteBuf
            |import java.io.IOException
            |
            |class GameTables(
            |    loader: IByteBufLoader,
            |) {
            |    internal val delegate = GameTablesGen { file -> loader.load(file) }
            |
            |    fun interface IByteBufLoader {
            |        @Throws(IOException::class)
            |        fun load(file: String): ByteBuf
            |    }
            |}
            |""".trimMargin()
    }

    private fun renderGeneratedGameTableTypes(entries: List<TableEntry>): String {
        val typeAliases = entries.joinToString("\n") {
            "typealias ${it.rowAlias} = ${it.rowFqcn}"
        }
        return """
            |package com.mikai233.common.config.luban
            |
            |$typeAliases
            |""".trimMargin()
    }

    private fun renderGeneratedGameTableAdapters(entries: List<TableEntry>): String {
        val adapters = entries.joinToString("\n\n") {
            buildString {
                when (val shape = it.shape) {
                    is TableShape.Keyed -> {
                        append("class ${it.adapterClass}(delegate: ${it.tableClassFqcn}) : OrderedMapConfigTable<${shape.keyType}, ${it.rowAlias}>(\n")
                        append("    name = ConfigTableName(\"${it.tableName}\"),\n")
                        append("    keyType = ${kclassLiteral(shape.keyType)},\n")
                        append("    rowType = ${it.rowAlias}::class,\n")
                        append("    rows = delegate.dataList.map { row -> row.${shape.keyField} to row },\n")
                        append(")")
                    }

                    is TableShape.ListLike -> {
                        append("class ${it.adapterClass}(delegate: ${it.tableClassFqcn}) : ListConfigTable<${it.rowAlias}>(\n")
                        append("    name = ConfigTableName(\"${it.tableName}\"),\n")
                        append("    rowType = ${it.rowAlias}::class,\n")
                        append("    rows = delegate.dataList,\n")
                        append(")")
                    }

                    is TableShape.Singleton -> {
                        append("class ${it.adapterClass}(delegate: ${it.tableClassFqcn}) : SingleConfigTable<${it.rowAlias}>(\n")
                        append("    name = ConfigTableName(\"${it.tableName}\"),\n")
                        append("    rowType = ${it.rowAlias}::class,\n")
                        append("    row = delegate.data(),\n")
                        append(")")
                    }
                }
            }
        }
        return """
            |package com.mikai233.common.config.luban
            |
            |import io.github.realmlabs.asteria.config.ConfigTableName
            |import io.github.realmlabs.asteria.config.ListConfigTable
            |import io.github.realmlabs.asteria.config.OrderedMapConfigTable
            |import io.github.realmlabs.asteria.config.SingleConfigTable
            |
            |$adapters
            |""".trimMargin()
    }

    private fun renderGeneratedGameTablesSnapshotBridge(entries: List<TableEntry>): String {
        return """
            |package com.mikai233.common.config.luban
            |
            |import io.github.realmlabs.asteria.config.SnapshotEntry
            |import io.github.realmlabs.asteria.config.luban.LubanSnapshotBridge
            |
            |object GameTablesSnapshotBridge : LubanSnapshotBridge<GameTables, GameTables.IByteBufLoader> {
            |    override val loaderType = GameTables.IByteBufLoader::class
            |
            |    override fun createTables(loader: GameTables.IByteBufLoader): GameTables {
            |        return GameTables(loader)
            |    }
            |
            |    override fun buildEntries(tables: GameTables): List<SnapshotEntry> {
            |        return listOf(
            |${renderBridgeEntries(entries)}
            |        )
            |    }
            |}
            |""".trimMargin()
    }

    private fun renderBridgeEntries(entries: List<TableEntry>): String {
        return entries.joinToString(",\n") { entry ->
            "            SnapshotEntry.Table(${entry.adapterClass}(tables.delegate.${entry.delegatePropertyName}), ${entry.adapterClass}::class)"
        }
    }

    private fun renderGeneratedGameTableAccessors(entries: List<TableEntry>): String {
        val accessors = entries.joinToString("\n\n") { entry ->
            """
            |val ConfigSnapshot.${entry.tableProperty}: ${entry.adapterClass}
            |    get() = table()
            """.trimMargin()
        }
        return """
            |package com.mikai233.common.config.luban
            |
            |import io.github.realmlabs.asteria.config.ConfigSnapshot
            |import io.github.realmlabs.asteria.config.table
            |
            |$accessors
            |""".trimMargin()
    }

    private fun renderGeneratedLubanMetadata(files: List<String>): String {
        val joined = files.joinToString(",\n") { "        \"$it\"" }
        return """
            |package com.mikai233.common.config.luban
            |
            |object GeneratedLubanMetadata {
            |    val files: List<String> = listOf(
            |$joined
            |    )
            |}
            |""".trimMargin()
    }

    private fun writeGeneratedFile(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content + "\n")
    }

    private fun toKotlinType(javaType: String): String {
        return when (javaType) {
            "Integer", "int" -> "Int"
            "Long", "long" -> "Long"
            "String" -> "String"
            else -> javaType
        }
    }

    private fun kclassLiteral(type: String): String {
        return when (type) {
            "Int" -> "Int::class"
            "Long" -> "Long::class"
            "String" -> "String::class"
            else -> "$type::class"
        }
    }

    private fun snakeToCamel(value: String): String {
        return camelToSnake(value).split('_').joinToString("") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } }
    }

    private fun decapitalize(value: String): String {
        return value.replaceFirstChar { it.lowercase(Locale.US) }
    }

    private fun camelToSnake(value: String): String {
        return value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase(Locale.US)
    }

    private fun pluralize(value: String): String {
        return when {
            value.endsWith("y") -> value.dropLast(1) + "ies"
            value.endsWith("s") -> value
            else -> value + "s"
        }
    }

    private data class TableEntry(
        val delegatePropertyName: String,
        val tableClassFqcn: String,
        val rowFqcn: String,
        val rowAlias: String,
        val adapterClass: String,
        val tableName: String,
        val tableProperty: String,
        val shape: TableShape,
    )

    private sealed interface TableShape {
        val rowFqcn: String

        data class Keyed(
            val keyType: String,
            override val rowFqcn: String,
            val keyField: String,
        ) : TableShape

        data class ListLike(
            override val rowFqcn: String,
        ) : TableShape

        data class Singleton(
            override val rowFqcn: String,
        ) : TableShape
    }
}
