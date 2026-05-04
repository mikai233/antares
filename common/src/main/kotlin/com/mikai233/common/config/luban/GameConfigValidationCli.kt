package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.component
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.coroutines.runBlocking

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
    val snapshot = loadSnapshot(generatedDataDir, includeQueries = mode == ValidationMode.QUERIES)
    if (mode == ValidationMode.QUERIES) {
        snapshot.component<GameConfigQueries>()
    }
}

private suspend fun loadSnapshot(generatedDataDir: Path, includeQueries: Boolean): ConfigSnapshot {
    check(generatedDataDir.exists()) {
        "Generated Luban data directory not found: $generatedDataDir. Run :common:exportLubanConfig first."
    }
    val bytesByPath = GeneratedLubanMetadata.files.associateWith { file ->
        Files.readAllBytes(generatedDataDir.resolve(file))
    }
    val builders = if (includeQueries) {
        GameConfigDerivedComponents.defaultBuilders
    } else {
        emptyList()
    }
    return GameConfigSnapshotLoader(
        delegate = LubanBinaryConfigLoader(
            tablesType = GameTables::class,
            dataSource = MemoryLubanDataSource(bytesByPath),
        ),
        componentBuilders = builders,
    ).load()
}

private fun defaultGeneratedDataDir(): Path {
    return Path.of(System.getProperty("common.buildDir"))
        .resolve("generated")
        .resolve("luban")
        .resolve("resources")
        .resolve("luban")
}
