package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.runtime.gameTimeOverrideStore
import com.mikai233.common.runtime.gameTimeSource
import com.mikai233.gm.GmNode
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Duration.Companion.milliseconds

@AllOpen
@RestController
@RequestMapping("/gm/api/game-time")
class GameTimeController(
    private val node: GmNode,
) {
    @GetMapping("/override")
    fun current(): GameTimeOverrideResponse = runBlocking {
        val override = node.gameTimeOverrideStore.current()
        GameTimeOverrideResponse(
            epoch = override.epoch,
            globalOffsetMillis = override.globalOffsetMillis,
            currentMillis = node.gameTimeSource.nowMillis(),
        )
    }

    @PostMapping("/override")
    fun update(@RequestBody request: UpdateGameTimeOverrideRequest): GameTimeOverrideResponse = runBlocking {
        val override = node.gameTimeOverrideStore.updateGlobalOffset(request.globalOffsetMillis)
        node.gameTimeSource.setGlobalOffset(override.globalOffsetMillis.milliseconds)
        GameTimeOverrideResponse(
            epoch = override.epoch,
            globalOffsetMillis = override.globalOffsetMillis,
            currentMillis = node.gameTimeSource.nowMillis(),
        )
    }

    @GetMapping("/reload-status")
    fun reloadStatus(@RequestParam epoch: Long): GameTimeReloadStatusResponse = runBlocking {
        GameTimeReloadStatusResponse(
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
