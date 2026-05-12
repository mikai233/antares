package com.mikai233.config.luban

import com.mikai233.config.luban.query.GameConfigQueryBuilders
import com.mikai233.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

private enum class ValidationMode {
    TABLES,
    QUERIES,
}

fun main(args: Array<String>) = runBlocking {
    require(args.isNotEmpty()) {
        "usage: GameConfigValidationCli <tables|queries> [generatedDataDir]"
    }
    val mode = ValidationMode.valueOf(args[0].uppercase())
    val generatedDataDir = args.getOrNull(1)?.let(Path::of) ?: defaultGeneratedDataDir()
    loadSnapshot(generatedDataDir, includeQueries = mode == ValidationMode.QUERIES)
}

private suspend fun loadSnapshot(generatedDataDir: Path, includeQueries: Boolean) {
    check(generatedDataDir.exists()) {
        "Generated Luban data directory not found: $generatedDataDir. Run :config:exportLubanConfig first."
    }
    val bytesByPath = GeneratedLubanMetadata.files.associateWith { file ->
        Files.readAllBytes(generatedDataDir.resolve(file))
    }
    val builders = if (includeQueries) {
        GameConfigQueryBuilders.defaultBuilders
    } else {
        emptyList()
    }
    val service = ConfigService(
        loader = LubanBinaryConfigLoader(
            tablesType = GameTables::class,
            dataSource = MemoryLubanDataSource(bytesByPath),
            bridge = GameTablesSnapshotBridge,
        ),
        validators = GameConfigValidators.defaultValidators,
        componentBuilders = builders,
    )
    service.load()
}

private fun defaultGeneratedDataDir(): Path {
    return Path.of(System.getProperty("config.buildDir"))
        .resolve("generated")
        .resolve("luban")
        .resolve("resources")
        .resolve("luban")
}
