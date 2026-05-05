package com.mikai233.common.runtime.module

import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.common.config.luban.GameConfigPublicationZipLoader
import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.event.GameConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigModule
import io.github.realmlabs.asteria.config.center.ConfigCenterReloadTrigger
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.config.center.ConfigWatchMode
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorSystem
import org.slf4j.LoggerFactory

class GameConfigModule : AsteriaModule {
    override val name: String = "game-config"

    private val logger = LoggerFactory.getLogger(GameConfigModule::class.java)
    private var delegate: AsteriaModule? = null

    override suspend fun install(context: ModuleContext) {
        val store = context.services.get(ConfigStore::class)
        delegate = ConfigModule {
            loader(
                GameConfigSnapshotLoader(
                    GameConfigPublicationZipLoader(
                        store = store,
                        layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION),
                    ),
                ),
            )
            onReload { result ->
                val event = result.changeEventOrNull() ?: return@onReload
                context.services.find(ActorSystem::class)?.eventStream?.publish(GameConfigChangedEvent.from(event))
            }
            hotReload {
                trigger(
                    ConfigCenterReloadTrigger(
                        store = store,
                        path = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION).currentPath,
                        mode = ConfigWatchMode.Value,
                    ),
                )
                onFailure { event ->
                    logger.error("game config hot reload failed", event.error)
                }
            }
        }.also { it.install(context) }
    }

    override suspend fun start(context: ModuleContext) {
        delegate?.start(context)
    }

    override suspend fun stop(context: ModuleContext) {
        delegate?.stop(context)
        delegate = null
    }
}
