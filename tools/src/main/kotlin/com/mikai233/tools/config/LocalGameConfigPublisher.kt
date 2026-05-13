package com.mikai233.tools.config

import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.config.luban.GameConfigPublicationZipLoader
import com.mikai233.config.luban.GameTables
import com.mikai233.config.luban.GameTablesSnapshotBridge
import com.mikai233.config.luban.gameConfigBundleMetadata
import com.mikai233.config.luban.query.GameConfigQueryBuilders
import com.mikai233.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.config.ConfigRevision
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationArtifact
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.config.publisher.ConfigPublisher

object LocalGameConfigPublisher {
    suspend fun publish(
        store: ZookeeperConfigStore,
    ) {
        val layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION)
        val bundleBytes = LubanPublishBundleArtifacts.bundleBytes()
        val entries = LubanPublishBundleArtifacts.unpackBundle(bundleBytes)
        val metadata = gameConfigBundleMetadata(entries)
        ConfigPublisher(
            loader = LubanBinaryConfigLoader(
                tablesType = GameTables::class,
                dataSource = MemoryLubanDataSource(entries),
                bridge = GameTablesSnapshotBridge,
                revisionFactory = { report ->
                    ConfigRevision(
                        version = metadata.version,
                        checksum = report.checksum,
                    )
                },
            ),
            artifactSource = {
                listOf(ConfigPublicationArtifact(LubanPublishBundleArtifacts.BUNDLE_FILE, bundleBytes))
            },
            store = store,
            layout = layout,
            validators = GameConfigValidators.defaultValidators,
            componentBuilders = GameConfigQueryBuilders.defaultBuilders,
        ).publish()
        ConfigService(
            loader = GameConfigPublicationZipLoader(
                store = store,
                layout = layout,
            ),
            validators = GameConfigValidators.defaultValidators,
            componentBuilders = GameConfigQueryBuilders.defaultBuilders,
        ).load()
    }
}
