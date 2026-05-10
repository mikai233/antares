package com.mikai233.buildlogic

import java.io.File

internal data class ScriptEntry(
    val simpleName: String,
    val qualifiedName: String,
)

internal class ScriptEntryScanner(
    private val projectDir: File,
    private val sourceSetName: String,
    private val baseTypeRegex: Regex = Regex("""\b(NodeScript|ActorScript)\b"""),
) {
    fun discover(): List<ScriptEntry> {
        val sourceRoot = projectDir.resolve("src/$sourceSetName")
        if (!sourceRoot.isDirectory) {
            return emptyList()
        }
        return sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension in SUPPORTED_EXTENSIONS }
            .flatMap(::discoverInFile)
            .distinctBy { it.qualifiedName }
            .sortedBy { it.qualifiedName }
            .toList()
    }

    private fun discoverInFile(file: File): Sequence<ScriptEntry> {
        val text = file.readText()
        val packageName = PACKAGE_DECLARATION_REGEX.find(text)?.groupValues?.get(1)
        val entryRegex = when (file.extension) {
            "kt" -> KOTLIN_SCRIPT_ENTRY_REGEX
            "groovy" -> GROOVY_SCRIPT_ENTRY_REGEX
            else -> return emptySequence()
        }
        return entryRegex.findAll(text).mapNotNull { match ->
            val baseType = match.groupValues[2]
            if (!baseTypeRegex.containsMatchIn(baseType)) {
                return@mapNotNull null
            }
            val simpleName = match.groupValues[1]
            ScriptEntry(
                simpleName = simpleName,
                qualifiedName = listOfNotNull(packageName, simpleName).joinToString("."),
            )
        }
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("kt", "groovy")
        val PACKAGE_DECLARATION_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][\w.]*)""")
        val KOTLIN_SCRIPT_ENTRY_REGEX =
            Regex("""(?m)^\s*(?:\w+\s+)*(?:class|object)\s+([A-Za-z_]\w*)\s*:\s*([^{\n]+)""")
        val GROOVY_SCRIPT_ENTRY_REGEX =
            Regex("""(?m)^\s*(?:\w+\s+)*class\s+([A-Za-z_]\w*)\s+extends\s+([^{\n]+)""")
    }
}
