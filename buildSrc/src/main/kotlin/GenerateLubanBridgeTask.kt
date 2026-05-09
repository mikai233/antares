import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import java.io.File
import java.util.*

abstract class GenerateLubanBridgeTask : DefaultTask() {
    @get:InputDirectory
    abstract val generatedJavaDir: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    abstract val generatedDataDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

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

        writeGeneratedFile(
            File(outDir, "GeneratedGameTables.kt"),
            renderGeneratedGameTables(),
        )
        writeGeneratedFile(
            File(outDir, "GeneratedGameTableTypes.kt"),
            renderGeneratedGameTableTypes(tableEntries),
        )
        writeGeneratedGameTablesSnapshotBridge(outDir, tableEntries)
        writeGeneratedLubanMetadata(outDir, artifactFiles)
        writeGeneratedFile(
            metadataFile.get().asFile,
            renderAsteriaConfigTableMetadata(tableEntries),
        )
        deleteStaleGeneratedFiles(
            outDir,
            "GeneratedGameTableAccessors.kt",
            "GeneratedGameTableAdapters.kt",
            "GeneratedGameConfigCatalog.kt",
            "GeneratedGameConfigTables.kt",
        )
    }

    private fun parseTableEntries(source: String): List<TableEntry> {
        val lineRegex = Regex("""_(\w+)\s*=\s*new\s+([\w.]+)\(loader\.load\("([^"]+)"\)\);""")
        return lineRegex.findAll(source).map { match ->
            val tableClassFqcn = match.groupValues[2]
            val tableName = match.groupValues[3]
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
                rowFqcn = rowFqcn,
                rowAlias = "${baseName}Row",
                tableRef = "Tb$baseName",
                tableMarker = "${baseName}ConfigTable",
                tableName = tableName,
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

    private fun writeGeneratedGameTablesSnapshotBridge(outDir: File, entries: List<TableEntry>) {
        val chunkedEntries = entries.chunked(BRIDGE_ENTRY_CHUNK_SIZE)
        deleteGeneratedChunks(outDir, "GeneratedGameTablesSnapshotBridgeChunk")
        writeGeneratedFile(
            File(outDir, "GeneratedGameTablesSnapshotBridge.kt"),
            renderGeneratedGameTablesSnapshotBridge(chunkedEntries.indices),
        )
        chunkedEntries.forEachIndexed { index, chunk ->
            writeGeneratedFile(
                File(outDir, "GeneratedGameTablesSnapshotBridgeChunk$index.kt"),
                renderGeneratedGameTablesSnapshotBridgeChunk(index, chunk),
            )
        }
    }

    private fun renderGeneratedGameTablesSnapshotBridge(indices: IntRange): String {
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
            |        return buildList {
            |${renderBridgeEntryChunkCalls(indices)}
            |        }
            |    }
            |}
            |""".trimMargin()
    }

    private fun renderBridgeEntryChunkCalls(indices: IntRange): String {
        return indices.joinToString("\n") { index ->
            "            addAll(buildGameTableSnapshotEntriesChunk$index(tables))"
        }
    }

    private fun renderGeneratedGameTablesSnapshotBridgeChunk(
        index: Int,
        entries: List<TableEntry>,
    ): String {
        return """
            |package com.mikai233.common.config.luban
            |
            |import io.github.realmlabs.asteria.config.SnapshotEntry
            |import io.github.realmlabs.asteria.config.listConfigTable
            |import io.github.realmlabs.asteria.config.orderedMapConfigTable
            |import io.github.realmlabs.asteria.config.singleConfigTable
            |
            |internal fun buildGameTableSnapshotEntriesChunk$index(tables: GameTables): List<SnapshotEntry> {
            |    return listOf(
            |${renderBridgeEntries(entries)}
            |    )
            |}
            |""".trimMargin()
    }

    private fun renderBridgeEntries(entries: List<TableEntry>): String {
        return entries.joinToString(",\n") { entry ->
            val source = "tables.delegate.${entry.delegatePropertyName}"
            val table = when (val shape = entry.shape) {
                is TableShape.Keyed ->
                    "orderedMapConfigTable(GameConfigTables.${entry.tableRef}, $source.dataList.map { row -> row.${shape.keyField} to row })"
                is TableShape.ListLike ->
                    "listConfigTable(GameConfigTables.${entry.tableRef}, $source.dataList)"
                is TableShape.Singleton ->
                    "singleConfigTable(GameConfigTables.${entry.tableRef}, $source.data())"
            }
            "        SnapshotEntry.Table($table)"
        }
    }

    private fun renderAsteriaConfigTableMetadata(entries: List<TableEntry>): String {
        val tables = entries.joinToString(",\n") { entry ->
            val fields = mutableListOf(
                "      \"name\": \"${jsonEscape(entry.tableName)}\"",
                "      \"shape\": \"${entry.shape.configShape}\"",
            )
            if (entry.shape is TableShape.Keyed) {
                fields += "      \"keyType\": \"${jsonEscape(entry.shape.keyType)}\""
            }
            fields += listOf(
                "      \"rowType\": \"${jsonEscape(entry.rowFqcn)}\"",
                "      \"tableType\": \"${entry.shape.tableType}\"",
                "      \"refName\": \"${jsonEscape(entry.tableRef)}\"",
                "      \"propertyName\": \"${jsonEscape(entry.tableProperty)}\"",
                "      \"markerName\": \"${jsonEscape(entry.tableMarker)}\"",
            )
            fields.joinToString(",\n", prefix = "    {\n", postfix = "\n    }")
        }
        return """
            |{
            |  "tables": [
            |$tables
            |  ]
            |}
            |""".trimMargin()
    }

    private fun writeGeneratedLubanMetadata(outDir: File, files: List<String>) {
        val chunkedFiles = files.chunked(METADATA_FILE_CHUNK_SIZE)
        deleteGeneratedChunks(outDir, "GeneratedLubanMetadataChunk")
        writeGeneratedFile(
            File(outDir, "GeneratedLubanMetadata.kt"),
            renderGeneratedLubanMetadata(chunkedFiles.indices),
        )
        chunkedFiles.forEachIndexed { index, chunk ->
            writeGeneratedFile(
                File(outDir, "GeneratedLubanMetadataChunk$index.kt"),
                renderGeneratedLubanMetadataChunk(index, chunk),
            )
        }
    }

    private fun renderGeneratedLubanMetadata(indices: IntRange): String {
        return """
            |package com.mikai233.common.config.luban
            |
            |object GeneratedLubanMetadata {
            |    val files: List<String> = buildList {
            |${renderMetadataFileChunkCalls(indices)}
            |    }
            |}
            |""".trimMargin()
    }

    private fun renderMetadataFileChunkCalls(indices: IntRange): String {
        return indices.joinToString("\n") { index ->
            "        addAll(generatedLubanMetadataFilesChunk$index())"
        }
    }

    private fun renderGeneratedLubanMetadataChunk(
        index: Int,
        files: List<String>,
    ): String {
        val joined = files.joinToString(",\n") { "        \"$it\"" }
        return """
            |package com.mikai233.common.config.luban
            |
            |internal fun generatedLubanMetadataFilesChunk$index(): List<String> {
            |    return listOf(
            |$joined
            |    )
            |}
            |""".trimMargin()
    }

    private fun writeGeneratedFile(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content.trimEnd() + "\n")
    }

    private fun deleteGeneratedChunks(
        outDir: File,
        prefix: String,
    ) {
        outDir.listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith(prefix) && file.extension == "kt" }
            ?.forEach { file -> file.delete() }
    }

    private fun deleteStaleGeneratedFiles(
        outDir: File,
        vararg names: String,
    ) {
        names.forEach { name ->
            File(outDir, name).delete()
        }
    }

    private fun jsonEscape(value: String): String {
        return buildString {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun toKotlinType(javaType: String): String {
        return when (javaType) {
            "Integer", "int" -> "kotlin.Int"
            "Long", "long" -> "kotlin.Long"
            "String" -> "kotlin.String"
            else -> javaType
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

    private data class TableEntry(
        val delegatePropertyName: String,
        val rowFqcn: String,
        val rowAlias: String,
        val tableRef: String,
        val tableMarker: String,
        val tableName: String,
        val tableProperty: String,
        val shape: TableShape,
    )

    private sealed interface TableShape {
        val rowFqcn: String
        val configShape: String
        val tableType: String

        data class Keyed(
            val keyType: String,
            override val rowFqcn: String,
            val keyField: String,
        ) : TableShape {
            override val configShape: String = "KEYED"
            override val tableType: String = "io.github.realmlabs.asteria.config.OrderedMapConfigTable"
        }

        data class ListLike(
            override val rowFqcn: String,
        ) : TableShape {
            override val configShape: String = "LIST"
            override val tableType: String = "io.github.realmlabs.asteria.config.ListConfigTable"
        }

        data class Singleton(
            override val rowFqcn: String,
        ) : TableShape {
            override val configShape: String = "SINGLETON"
            override val tableType: String = "io.github.realmlabs.asteria.config.SingleConfigTable"
        }
    }

    private companion object {
        const val BRIDGE_ENTRY_CHUNK_SIZE = 50
        const val METADATA_FILE_CHUNK_SIZE = 50
    }
}
