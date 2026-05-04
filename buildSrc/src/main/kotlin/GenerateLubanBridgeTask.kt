import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale

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

        val gameTablesGen = File(javaDir, "GameTablesGen.java")
        require(gameTablesGen.isFile) { "generated Luban tables entry not found: $gameTablesGen" }

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
            renderGeneratedGameTables(tableEntries),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedLubanMetadata.kt"),
            renderGeneratedLubanMetadata(artifactFiles),
        )
    }

    private fun parseTableEntries(source: String): List<TableEntry> {
        val lineRegex = Regex("""_(\w+)\s*=\s*new\s+([\w.]+)\(loader\.load\("([^"]+)"\)\);""")
        return lineRegex.findAll(source).map { match ->
            val fieldName = match.groupValues[1]
            val tableClassFqcn = match.groupValues[2]
            val delegateGetterName = "get${fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}"
            val tableFile = File(generatedJavaDir.get().asFile, tableClassFqcn.removePrefix("com.mikai233.common.config.luban.gen.").replace('.', '/') + ".java")
            require(tableFile.isFile) { "generated Luban table source not found: $tableFile" }
            val tableSource = tableFile.readText()
            val mapRegex = Regex("""HashMap<([^,>]+),\s*([^>]+)>\s+_dataMap""")
            val mapMatch = requireNotNull(mapRegex.find(tableSource)) { "unable to parse data map in $tableFile" }
            val keyType = mapMatch.groupValues[1].trim()
            val rowFqcn = mapMatch.groupValues[2].trim()
            val rowSimple = rowFqcn.substringAfterLast('.')
            val baseName = snakeToCamel(rowSimple)
            TableEntry(
                delegateGetterName = delegateGetterName,
                publicGetterName = "getTb$baseName",
                tableClassFqcn = tableClassFqcn,
                rowFqcn = rowFqcn,
                rowAlias = "${baseName}Row",
                adapterClass = "Tb$baseName",
                tableName = pluralize(camelToSnake(baseName)),
                keyType = toKotlinType(keyType),
                rowSimple = rowSimple,
                tableField = baseName.replaceFirstChar { it.lowercase(Locale.US) } + "Table",
                helperByType = rowSimple == "item",
            )
        }.toList()
    }

    private fun renderGeneratedGameTables(entries: List<TableEntry>): String {
        val typeAliases = entries.joinToString("\n") {
            "typealias ${it.rowAlias} = ${it.rowFqcn}"
        }
        val fields = entries.joinToString("\n") {
            "    private val ${it.tableField} by lazy { ${it.adapterClass}(delegate.${it.delegateGetterName}()) }"
        }
        val getters = entries.joinToString("\n\n") {
            "    fun ${it.publicGetterName}(): ${it.adapterClass} = ${it.tableField}"
        }
        val adapters = entries.joinToString("\n\n") {
            buildString {
                append("class ${it.adapterClass}(delegate: ${it.tableClassFqcn}) : GameMapConfigTable<${it.keyType}, ${it.rowAlias}>(\n")
                append("    name = \"${it.tableName}\",\n")
                append("    keyType = ${kclassLiteral(it.keyType)},\n")
                append("    rowType = ${it.rowAlias}::class,\n")
                append("    rows = delegate.getDataMap(),\n")
                append(")")
                if (it.helperByType) {
                    append(" {\n")
                    append("    fun byType(type: Int): List<${it.rowAlias}> {\n")
                    append("        return all().filter { row -> row.type == type }\n")
                    append("    }\n")
                    append("}")
                }
            }
        }

        return """
            |package com.mikai233.common.config.luban
            |
            |import com.mikai233.common.config.luban.gen.GameTablesGen
            |import java.io.IOException
            |import luban.ByteBuf
            |
            |class GameTables(
            |    loader: IByteBufLoader,
            |) {
            |    private val delegate = GameTablesGen(
            |        GameTablesGen.IByteBufLoader { file -> loader.load(file) },
            |    )
            |
            |$fields
            |
            |$getters
            |
            |    fun interface IByteBufLoader {
            |        @Throws(IOException::class)
            |        fun load(file: String): ByteBuf
            |    }
            |}
            |
            |$typeAliases
            |
            |$adapters
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
        val delegateGetterName: String,
        val publicGetterName: String,
        val tableClassFqcn: String,
        val rowFqcn: String,
        val rowAlias: String,
        val adapterClass: String,
        val tableName: String,
        val keyType: String,
        val rowSimple: String,
        val tableField: String,
        val helperByType: Boolean,
    )
}
