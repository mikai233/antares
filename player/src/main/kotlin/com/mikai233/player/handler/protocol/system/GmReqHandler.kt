package com.mikai233.player.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.handler.gm.TestGmHandler
import com.mikai233.protocol.ProtoRpcChat.PlayerAllianceChangedReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.gmResp
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class GmReqHandler(
    private val testGmHandler: TestGmHandler,
) : PlayerMessageHandler<GmReq> {
    override fun handle(context: PlayerHandlerContext, message: GmReq) {
        val actor = context.actor
        invokeOnTargetMode(actor.node.runtimeEnv.serverMode, ServerMode.DevMode) {
            when (message.cmd) {
                "testGm" -> testGmHandler.handle(actor, message.paramsList)
                "setAlliance" -> {
                    val allianceId = message.paramsList.firstOrNull()?.toLongOrNull()
                        ?: error("setAlliance requires alliance id")
                    actor.node.chatService.handleAllianceChanged(
                        actor,
                        PlayerAllianceChangedReq.newBuilder()
                            .setPlayerId(actor.playerId)
                            .setAllianceId(allianceId)
                            .build(),
                    )
                    actor.send(
                        gmResp {
                            success = true
                            data = "alliance_id=$allianceId"
                        },
                    )
                }

                "muteChat" -> {
                    val durationSeconds = message.paramsList.firstOrNull()?.toLongOrNull()
                        ?: error("muteChat requires duration seconds")
                    val mutedUntil = unixTimestamp() + durationSeconds.coerceAtLeast(0) * 1000
                    actor.node.chatService.setChatMutedUntil(actor, mutedUntil)
                    actor.send(
                        gmResp {
                            success = true
                            data = "chat_muted_until=$mutedUntil"
                        },
                    )
                }

                "clearChatMute" -> {
                    actor.node.chatService.setChatMutedUntil(actor, 0)
                    actor.send(
                        gmResp {
                            success = true
                            data = "chat mute cleared"
                        },
                    )
                }

                "clearChatRateLimit" -> {
                    actor.node.chatService.clearRateLimit(actor)
                    actor.send(
                        gmResp {
                            success = true
                            data = "chat rate limit cleared"
                        },
                    )
                }

                else -> error("gm handler for command=${message.cmd} not found")
            }
        }
    }
}
