package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.ask
import com.mikai233.gm.GmNode
import com.mikai233.protocol.ProtoRpcShutdown.ShutdownPhase
import com.mikai233.protocol.ProtoRpcShutdown.ShutdownStartReq
import com.mikai233.protocol.ProtoRpcShutdown.ShutdownStatusReq
import com.mikai233.protocol.ProtoRpcShutdown.ShutdownStatusResp
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@AllOpen
@RestController
@RequestMapping("/gm/api/shutdown")
class ShutdownController(
    private val node: GmNode,
) {
    @GetMapping("/status")
    fun status(): ShutdownStatusResponse = runBlocking {
        node.shutdownCoordinator.ask<ShutdownStatusResp>(
            ShutdownStatusReq.getDefaultInstance(),
        ).getOrThrow().toResponse()
    }

    @PostMapping("/start")
    fun start(@RequestBody(required = false) request: StartShutdownRequest?): ShutdownStatusResponse = runBlocking {
        node.shutdownCoordinator.ask<ShutdownStatusResp>(
            ShutdownStartReq.newBuilder()
                .setPlanId(request?.planId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString())
                .setRequestedBy(request?.requestedBy?.takeIf(String::isNotBlank) ?: "gm")
                .build(),
        ).getOrThrow().toResponse()
    }
}

data class StartShutdownRequest(
    val planId: String? = null,
    val requestedBy: String? = null,
)

data class ShutdownStatusResponse(
    val planId: String?,
    val phase: String,
    val requestedBy: String?,
    val expectedGateCount: Int,
    val drainedGateCount: Int,
    val expectedPlayerCount: Int,
    val flushedPlayerCount: Int,
    val expectedWorldCount: Int,
    val flushedWorldCount: Int,
    val errors: List<String>,
)

private fun ShutdownStatusResp.toResponse(): ShutdownStatusResponse {
    return ShutdownStatusResponse(
        planId = planId.takeIf(String::isNotBlank),
        phase = phase.normalizedName(),
        requestedBy = requestedBy.takeIf(String::isNotBlank),
        expectedGateCount = expectedGateCount,
        drainedGateCount = drainedGateCount,
        expectedPlayerCount = expectedPlayerCount,
        flushedPlayerCount = flushedPlayerCount,
        expectedWorldCount = expectedWorldCount,
        flushedWorldCount = flushedWorldCount,
        errors = errorsList,
    )
}

private fun ShutdownPhase.normalizedName(): String {
    return name.removePrefix("SHUTDOWN_PHASE_").lowercase()
}
