package com.mikai233.player.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.runtime.gameTimeSource
import com.mikai233.player.PlayerActor
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.player.handler.gm.TestGmHandler
import com.mikai233.protocol.ProtoRpcChat.PlayerAllianceChangedReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.gmResp
import io.github.realmlabs.asteria.message.AsteriaMessageHandler
import kotlin.time.Duration.Companion.milliseconds

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class GmReqHandler(
    private val testGmHandler: TestGmHandler,
) : PlayerMessageHandler<GmReq> {
    override fun handle(context: PlayerHandlerContext, message: GmReq) {
        val actor = context.actor
        invokeOnTargetMode(actor.node.runtimeEnv.serverMode, ServerMode.DevMode) {
            if (handleGameTimeCommand(actor, message)) {
                return@invokeOnTargetMode
            }
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
                    val mutedUntil = actor.gameTime.nowMillis() + durationSeconds.coerceAtLeast(0) * 1000
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

    private fun handleGameTimeCommand(actor: PlayerActor, message: GmReq): Boolean {
        when (message.cmd) {
            "setGlobalTimeOffsetMillis" -> {
                actor.node.gameTimeSource.setGlobalOffset(requiredMillis(message).milliseconds)
            }

            "addGlobalTimeOffsetMillis" -> {
                actor.node.gameTimeSource.addGlobalOffset(requiredMillis(message).milliseconds)
            }

            "resetGlobalTimeOffset" -> actor.node.gameTimeSource.resetGlobalOffset()
            "setActorTimeOffsetMillis" -> actor.gameTime.setActorOffset(requiredMillis(message).milliseconds)
            "addActorTimeOffsetMillis" -> actor.gameTime.addActorOffset(requiredMillis(message).milliseconds)
            "resetActorTimeOffset" -> actor.gameTime.resetActorOffset()
            "showGameTime" -> Unit
            else -> return false
        }
        actor.sendGameTimeResp()
        return true
    }

    private fun requiredMillis(message: GmReq): Long {
        return message.paramsList.firstOrNull()?.toLongOrNull()
            ?: error("${message.cmd} requires offset millis")
    }

    private fun PlayerActor.sendGameTimeResp() {
        send(
            gmResp {
                success = true
                data = "now=${gameTime.nowMillis()}, " +
                        "local=${gameTime.nowLocal()}, " +
                        "zone=${gameTime.timeZone.id}, " +
                        "globalOffset=${gameTime.globalOffset()}, " +
                        "actorOffset=${gameTime.actorOffset()}, " +
                        "totalOffset=${gameTime.totalOffset()}"
            },
        )
    }
}
