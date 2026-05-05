package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigLoader
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.center.ConfigCodec
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.config.center.JacksonConfigCodec
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationConsumer
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class GameConfigPublicationZipLoader(
    store: ConfigStore,
    layout: ConfigPublicationLayout,
    codec: ConfigCodec = JacksonConfigCodec(),
    private val artifactPath: String = DEFAULT_ARTIFACT_PATH,
) : ConfigLoader {
    private val consumer = ConfigPublicationConsumer(store, layout, codec)

    override suspend fun load(): ConfigSnapshot {
        val bundle = consumer.loadCurrent()
        val zipBytes = requireNotNull(bundle.artifacts[artifactPath]) {
            "game config publication artifact $artifactPath not found"
        }
        return LubanBinaryConfigLoader(
            tablesType = GameTables::class,
            dataSource = MemoryLubanDataSource(unpackZip(zipBytes)),
            bridge = GameTablesSnapshotBridge,
            revisionFactory = { bundle.manifest.revision },
        ).load()
    }

    private fun unpackZip(bytes: ByteArray): Map<String, ByteArray> {
        return linkedMapOf<String, ByteArray>().also { filesByPath ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        filesByPath[entry.name] = zip.readAllBytes()
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_ARTIFACT_PATH: String = "game-config.zip"
    }
}
