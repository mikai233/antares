package com.mikai233.common.runtime.module

import com.mikai233.common.runtime.NoopStartupLikeReloadPlan
import com.mikai233.common.runtime.StartupLikeReloadPlan
import com.mikai233.common.time.ConfigCenterGameTimeOverrideStore
import com.mikai233.common.time.GameTimeOverride
import com.mikai233.common.time.GameTimeOverrideStore
import com.mikai233.common.time.GameTimeReloadAck
import com.mikai233.common.time.GameTimeSource
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GameTimeReloadModule(
    private val nodeId: String,
) : AsteriaModule {
    override val name: String = "game-time-reload"

    private val logger = LoggerFactory.getLogger(GameTimeReloadModule::class.java)
    private val appliedEpoch = AtomicLong(-1)
    private var watchJob: Job? = null

    override suspend fun install(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val gameTimeSource = context.services.get(GameTimeSource::class)
        val store = ConfigCenterGameTimeOverrideStore(repository)
        val override = store.ensureInitial(gameTimeSource.globalOffset().inWholeMilliseconds)
        gameTimeSource.setGlobalOffset(override.globalOffsetMillis.milliseconds)
        appliedEpoch.set(override.epoch)
        context.services.register(GameTimeOverrideStore::class, store)
    }

    override suspend fun start(context: ModuleContext) {
        val scope = context.services.get(CoroutineScope::class)
        val store = context.services.get(GameTimeOverrideStore::class)
        val gameTimeSource = context.services.get(GameTimeSource::class)
        val plan = context.services.find(StartupLikeReloadPlan::class) ?: NoopStartupLikeReloadPlan
        watchJob = scope.launch {
            watchGameTimeOverride(context, store, gameTimeSource, plan)
        }
    }

    override suspend fun stop(context: ModuleContext) {
        watchJob?.cancelAndJoin()
        watchJob = null
    }

    private suspend fun watchGameTimeOverride(
        context: ModuleContext,
        store: GameTimeOverrideStore,
        gameTimeSource: GameTimeSource,
        plan: StartupLikeReloadPlan,
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                applyOverride(context, store, gameTimeSource, plan, store.current())
                store.watch().collect { override ->
                    applyOverride(context, store, gameTimeSource, plan, override)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("game time override watch failed; retrying", error)
                delay(5.seconds)
            }
        }
    }

    private suspend fun applyOverride(
        context: ModuleContext,
        store: GameTimeOverrideStore,
        gameTimeSource: GameTimeSource,
        plan: StartupLikeReloadPlan,
        override: GameTimeOverride,
    ) {
        val previousEpoch = appliedEpoch.get()
        if (override.epoch <= previousEpoch) {
            return
        }
        if (!appliedEpoch.compareAndSet(previousEpoch, override.epoch)) {
            return
        }
        val planId = "game-time-${override.epoch}"
        logger.info(
            "applying game time override epoch={} offsetMillis={} nodeId={} roles={}",
            override.epoch,
            override.globalOffsetMillis,
            nodeId,
            context.roles.joinToString { it.value },
        )
        val result = runCatching {
            gameTimeSource.setGlobalOffset(override.globalOffsetMillis.milliseconds)
            plan.reload(planId)
        }
        val role = context.roles.joinToString(",") { it.value }.ifBlank { "unknown" }
        val ack = GameTimeReloadAck(
            epoch = override.epoch,
            nodeId = nodeId,
            role = role,
            success = result.isSuccess,
            error = result.exceptionOrNull()?.message,
        )
        store.ack(ack)
        result.onSuccess { reloadResult ->
            logger.info(
                "game time override applied epoch={} nodeId={} stoppedActors={} failedActors={}",
                override.epoch,
                nodeId,
                reloadResult.stoppedActors,
                reloadResult.failedActors,
            )
        }.onFailure { error ->
            logger.error("game time override apply failed epoch={} nodeId={}", override.epoch, nodeId, error)
        }
    }
}
