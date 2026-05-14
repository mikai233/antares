package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.runtime.WorldRuntimeState
import com.mikai233.common.runtime.WorldRuntimeStatus
import com.mikai233.common.runtime.gameWorldConfigs
import com.mikai233.common.runtime.worldRuntimeStateStore
import com.mikai233.gm.GmNode
import kotlinx.datetime.LocalDateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@AllOpen
@RestController
@RequestMapping("/gm/api/worlds")
class WorldStatusController(
    private val node: GmNode,
) {
    @GetMapping("/status")
    @Suppress("LongParameterList")
    suspend fun status(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) worldId: Long?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) status: WorldRuntimeStatus?,
        @RequestParam(required = false) configured: Boolean?,
        @RequestParam(required = false) heartbeat: WorldHeartbeatFilter?,
        @RequestParam(required = false) openFrom: String?,
        @RequestParam(required = false) openTo: String?,
        @RequestParam(required = false) staleAfterMillis: Long?,
    ): WorldStatusListResponse {
        require(page > 0) { "page must be greater than zero" }
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be between 1 and $MAX_PAGE_SIZE" }
        val staleThreshold = staleAfterMillis ?: DEFAULT_STALE_AFTER_MILLIS
        require(staleThreshold > 0) { "staleAfterMillis must be greater than zero" }
        val nameKeyword = name?.trim()?.takeIf { it.isNotEmpty() }
        val openFromTime = parseOpenDateTime(openFrom)
        val openToTime = parseOpenDateTime(openTo)

        val now = System.currentTimeMillis()
        val configs = node.gameWorldConfigs
        val states = node.worldRuntimeStateStore.list().associateBy { it.worldId }
        val worldIds = (configs.keys + states.keys).sorted()
        val allWorlds = worldIds.map { worldId ->
            val config = configs[worldId]
            val state = states[worldId]
            worldStatusRow(
                worldId = worldId,
                config = config,
                state = state,
                now = now,
                staleAfterMillis = staleThreshold,
            )
        }
        val filteredWorlds = allWorlds.asSequence()
            .filter { world -> worldId == null || world.worldId == worldId }
            .filter { world -> nameKeyword == null || world.name?.contains(nameKeyword, ignoreCase = true) == true }
            .filter { world -> status == null || world.status == status }
            .filter { world -> configured == null || world.configured == configured }
            .filter { world -> heartbeat == null || heartbeat.matches(world) }
            .filter { world -> openFromTime == null || isOpenAtOrAfter(world, openFromTime) }
            .filter { world -> openToTime == null || isOpenAtOrBefore(world, openToTime) }
            .toList()
        val offset = (page - 1) * pageSize
        return WorldStatusListResponse(
            staleAfterMillis = staleThreshold,
            page = page,
            pageSize = pageSize,
            total = filteredWorlds.size,
            totalWorlds = allWorlds.size,
            upWorlds = allWorlds.count { it.status == WorldRuntimeStatus.Up },
            loadingWorlds = allWorlds.count { it.status == WorldRuntimeStatus.Loading },
            downWorlds = allWorlds.count { it.status == WorldRuntimeStatus.Down },
            staleWorlds = allWorlds.count { it.stale },
            worlds = filteredWorlds.drop(offset).take(pageSize),
        )
    }

    private fun worldStatusRow(
        worldId: Long,
        config: GameWorldConfig?,
        state: WorldRuntimeState?,
        now: Long,
        staleAfterMillis: Long,
    ): WorldStatusResponse {
        val stale = state != null && now - state.updatedAtMillis > staleAfterMillis
        return WorldStatusResponse(
            worldId = worldId,
            name = config?.name,
            configured = config != null,
            status = when {
                state == null -> WorldRuntimeStatus.Down
                stale -> WorldRuntimeStatus.Down
                else -> state.status
            },
            reportedStatus = state?.status,
            stale = stale,
            nodeId = state?.nodeId,
            nodeAddress = state?.nodeAddress,
            updatedAtMillis = state?.updatedAtMillis,
            message = when {
                state == null -> "not reported"
                stale -> "heartbeat stale"
                else -> state.message
            },
            openDateTime = config?.openDateTime,
            onlineLimit = config?.onlineLimit,
            registerLimit = config?.registerLimit,
        )
    }

    private fun parseOpenDateTime(value: String?): LocalDateTime? =
        value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { LocalDateTime.parse(it, LocalDateTime.Formats.ISO) }

    private fun isOpenAtOrAfter(world: WorldStatusResponse, from: LocalDateTime): Boolean =
        parseOpenDateTime(world.openDateTime)?.let { it >= from } == true

    private fun isOpenAtOrBefore(world: WorldStatusResponse, to: LocalDateTime): Boolean =
        parseOpenDateTime(world.openDateTime)?.let { it <= to } == true

    private companion object {
        const val DEFAULT_STALE_AFTER_MILLIS = 30_000L
        const val MAX_PAGE_SIZE = 200
    }
}

enum class WorldHeartbeatFilter {
    Healthy,
    Unhealthy,
    ;

    fun matches(world: WorldStatusResponse): Boolean =
        when (this) {
            Healthy -> world.updatedAtMillis != null && !world.stale
            Unhealthy -> world.updatedAtMillis == null || world.stale
        }
}

data class WorldStatusListResponse(
    val staleAfterMillis: Long,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalWorlds: Int,
    val upWorlds: Int,
    val loadingWorlds: Int,
    val downWorlds: Int,
    val staleWorlds: Int,
    val worlds: List<WorldStatusResponse>,
)

data class WorldStatusResponse(
    val worldId: Long,
    val name: String?,
    val configured: Boolean,
    val status: WorldRuntimeStatus,
    val reportedStatus: WorldRuntimeStatus?,
    val stale: Boolean,
    val nodeId: String?,
    val nodeAddress: String?,
    val updatedAtMillis: Long?,
    val message: String?,
    val openDateTime: String?,
    val onlineLimit: Long?,
    val registerLimit: Long?,
)
