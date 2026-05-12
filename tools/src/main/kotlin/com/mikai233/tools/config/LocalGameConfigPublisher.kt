package com.mikai233.tools.config

import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.config.luban.GameConfigPublicationZipLoader
import com.mikai233.config.luban.GameTables
import com.mikai233.config.luban.GameTablesSnapshotBridge
import com.mikai233.config.luban.query.GameConfigQueryBuilders
import com.mikai233.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.config.ConfigRevision
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigStore
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.config.publisher.ConfigPublisher

object LocalGameConfigPublisher {
    suspend fun publish(
        store: ZookeeperConfigStore,
        options: GameConfigPublishOptions = GameConfigPublishOptions.fromEnvironment(),
    ) {
        val layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION)
        ConfigPublisher(
            loader = LubanBinaryConfigLoader(
                tablesType = GameTables::class,
                dataSource = MemoryLubanDataSource(
                    LubanPublishBundleArtifacts.unpackBundle(LubanPublishBundleArtifacts.bundleBytes()),
                ),
                bridge = GameTablesSnapshotBridge,
                revisionFactory = { report ->
                    ConfigRevision(
                        version = options.version ?: report.checksum,
                        checksum = report.checksum,
                    )
                },
            ),
            artifactSource = { listOf(LubanPublishBundleArtifacts.bundleArtifact()) },
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
