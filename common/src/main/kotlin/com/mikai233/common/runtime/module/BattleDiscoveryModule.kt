package com.mikai233.common.runtime.module

import com.mikai233.common.battle.BattleEndpointRegistry
import com.mikai233.common.battle.BattleInstance
import com.mikai233.common.battle.BattleInstanceState
import com.mikai233.common.config.BATTLE_INSTANCES
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class BattleDiscoveryModule(
    private val registry: BattleEndpointRegistry,
) : AsteriaModule {
    override val name: String = "battle-discovery"

    private val logger = LoggerFactory.getLogger(BattleDiscoveryModule::class.java)
    private var watchJob: Job? = null

    override suspend fun start(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val scope = context.services.get(CoroutineScope::class)
        watchJob = scope.launch {
            watchBattleInstances(repository)
        }
    }

    override suspend fun stop(context: ModuleContext) {
        watchJob?.cancelAndJoin()
        watchJob = null
    }

    private suspend fun watchBattleInstances(repository: RuntimeConfigRepository) {
        while (currentCoroutineContext().isActive) {
            try {
                repository.watchChildren<BattleInstance>(BATTLE_INSTANCES, emitInitial = true)
                    .collect { snapshot ->
                        val endpoints = snapshot.values.values
                            .map { it.value }
                            .filter { it.state == BattleInstanceState.UP }
                            .map { it.toEndpoint() }
                        registry.replaceDiscovered(endpoints)
                        logger.info(
                            "battle instances refreshed discovered={} activeEndpoints={} effectiveEndpoints={}",
                            snapshot.values.size,
                            endpoints.size,
                            registry.endpoints.size,
                        )
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("battle discovery watch failed; retrying", error)
                delay(5.seconds)
            }
        }
    }
}
