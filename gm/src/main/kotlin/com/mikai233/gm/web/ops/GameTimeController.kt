package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.runtime.gameTimeOverrideStore
import com.mikai233.common.runtime.gameTimeSource
import com.mikai233.gm.GmNode
import org.springframework.web.bind.annotation.*
import kotlin.time.Duration.Companion.milliseconds

@AllOpen
@RestController
@RequestMapping("/gm/api/game-time")
class GameTimeController(
    private val node: GmNode,
) {
    @GetMapping("/override")
    suspend fun current(): GameTimeOverrideResponse {
        val override = node.gameTimeOverrideStore.current()
        return GameTimeOverrideResponse(
            epoch = override.epoch,
            globalOffsetMillis = override.globalOffsetMillis,
            currentMillis = node.gameTimeSource.nowMillis(),
        )
    }

    @PostMapping("/override")
    suspend fun update(@RequestBody request: UpdateGameTimeOverrideRequest): GameTimeOverrideResponse {
        val override = node.gameTimeOverrideStore.updateGlobalOffset(request.globalOffsetMillis)
        node.gameTimeSource.setGlobalOffset(override.globalOffsetMillis.milliseconds)
        return GameTimeOverrideResponse(
            epoch = override.epoch,
            globalOffsetMillis = override.globalOffsetMillis,
            currentMillis = node.gameTimeSource.nowMillis(),
        )
    }

    @GetMapping("/reload-status")
    suspend fun reloadStatus(@RequestParam epoch: Long): GameTimeReloadStatusResponse {
        return GameTimeReloadStatusResponse(
            epoch = epoch,
            acks = node.gameTimeOverrideStore.acks(epoch).map {
                GameTimeReloadAckResponse(
                    nodeId = it.nodeId,
                    role = it.role,
                    success = it.success,
                    error = it.error,
                )
            },
        )
    }
}

data class UpdateGameTimeOverrideRequest(
    val globalOffsetMillis: Long,
)

data class GameTimeOverrideResponse(
    val epoch: Long,
    val globalOffsetMillis: Long,
    val currentMillis: Long,
)

data class GameTimeReloadStatusResponse(
    val epoch: Long,
    val acks: List<GameTimeReloadAckResponse>,
)

data class GameTimeReloadAckResponse(
    val nodeId: String,
    val role: String,
    val success: Boolean,
    val error: String?,
)
