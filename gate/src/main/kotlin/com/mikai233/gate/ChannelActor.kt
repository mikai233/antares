package com.mikai233.gate


import akka.actor.Props
import akka.actor.ReceiveTimeout
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.kotlin.toByteString
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.connectionExpiredNotify
import com.mikai233.protocol.pingResp
import com.mikai233.shared.codec.CryptoCodec
import com.mikai233.shared.formatMessage
import com.mikai233.shared.message.*
import com.mikai233.shared.message.world.PlayerLogin
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ChannelActor(node: GateNode, private val handlerContext: ChannelHandlerContext) : StatefulActor<GateNode>(node) {
    companion object {
        val MaxIdleDuration = 10.seconds

        fun props(node: GateNode, handlerContext: ChannelHandlerContext): Props =
            Props.create(ChannelActor::class.java, node, handlerContext)
    }

    private var playerId: Long? = null
    private var birthWorldId: Long? = null
    private var worldId: Long? = null
    private lateinit var clientPublicKey: ByteArray

    override fun preStart() {
        super.preStart()
        logger.debug("{} preStart", self)
        context.setReceiveTimeout(MaxIdleDuration.toJavaDuration())
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) { handleClientConnectMessage(it) }
            .match(ServerProtobuf::class.java) {
                logger.warning(
                    "{} receive unexpected server side message:{} when not authorized",
                    self,
                    formatMessage(it.message),
                )
            }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    private fun write(message: GeneratedMessage, listener: ChannelFutureListener? = null) {
        val future = handlerContext.writeAndFlush(message)
        listener?.let {
            future.addListener(listener)
        }
    }

    private fun handleClientConnectMessage(clientProtobuf: ClientProtobuf) {
        val message = clientProtobuf.message
        return if (message is LoginReq) {
            clientPublicKey = message.clientPublicKey.toByteArray()
            worldId = message.worldId
            node.worldSharding.tell(PlayerLogin(message.worldId, message), self)
            context.become(authenticating())
        } else {
            logger.warning(
                "{} receive unexpected client side message:{} when not authorized",
                self,
                formatMessage(message),
            )
            stopChannel()
        }
    }

    private fun tryJudgeAuthResult(message: GeneratedMessage) {
        if (message is LoginResp) {
            when (message.result) {
                ProtoLogin.LoginResult.Success -> {
                    handleLoginSuccess(message)
                }

                ProtoLogin.LoginResult.RegisterLimit,
                ProtoLogin.LoginResult.WorldNotExists,
                ProtoLogin.LoginResult.WorldClosed,
                ProtoLogin.LoginResult.AccountBan -> {
                    handleLoginFailed(message)
                }

                ProtoLogin.LoginResult.UNRECOGNIZED, null -> {
                    logger.error("unknown login result, stop the channel")
                    handleLoginFailed(message)
                }
            }
        } else {
            logger.error("unexpected server message:{} while authenticating", formatMessage(message))
            stopChannel()
        }
    }

    private fun handleLoginSuccess(resp: LoginResp) {
        val playerData = resp.data
        playerId = playerData.playerId
        val serverKeyPair = ECDH.genKeyPair()
        val keyResp = resp.toBuilder().setServerPublicKey(serverKeyPair.publicKey.toByteString()).build()
        write(keyResp) {
            if (it.isDone) {
                val shareKey = ECDH.calculateShareKey(serverKeyPair.privateKey, clientPublicKey)
                handlerContext.channel().attr(CryptoCodec.cryptoKey).set(shareKey)
                self tell ChannelAuthorized
            }
        }
    }

    private fun handleLoginFailed(resp: LoginResp) {
        write(resp)
        stopChannel()
    }

    private fun authenticating(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) {
                val message = it.message
                logger.warning(
                    "{} unexpected client message:{} while authenticating, stop the channel",
                    self,
                    formatMessage(message)
                )
                stopChannel()
            }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ServerProtobuf::class.java) { tryJudgeAuthResult(it.message) }
            .match(StopChannel::class.java) { stopChannel() }
            .match(ChannelAuthorized::class.java) { context.become(authorized()) }
            .match(ReceiveTimeout::class.java) { stopChannel() }
            .build()
    }

    private fun handleChannelExpired(message: ChannelExpired) {
        write(connectionExpiredNotify { reasonValue = message.reason })
        stopChannel()
    }

    private fun authorized(): Receive {
        return receiveBuilder()
            .match(ClientProtobuf::class.java) { forwardClientMessage(it) }
            .match(ChannelExpired::class.java) { handleChannelExpired(it) }
            .match(ServerProtobuf::class.java) {
                invokeOnTargetMode(setOf(ServerMode.DevMode)) {
                    logger.info(
                        "{} playerId:{} worldId:{} receive server message:{}",
                        self,
                        playerId,
                        worldId,
                        formatMessage(it.message)
                    )
                }
                write(it.message)
            }
            .match(StopChannel::class.java) { stopChannel() }
            .build()
    }

    /**
     * 客户端主动断开连接
     */
    private fun stopChannel() {
        handlerContext.close()
        context.stop(self)
    }

    private fun forwardClientMessage(clientProtobuf: ClientProtobuf) {
        val message = clientProtobuf.message
        if (message is PingReq) {
            handlePingReq()
        } else {
            forwardToActor(clientProtobuf)
        }
    }

    private fun handlePingReq() {
        write(pingResp { serverTimestamp = unixTimestamp() })
    }

    private fun forwardToActor(clientProtobuf: ClientProtobuf) {
        val (id, message) = clientProtobuf
        val target = MessageForward.whichActor(id)
        logger.debug("forward message:{} to target:{}", formatMessage(message), target)
        if (target == null) {
            logger.warning("proto: {} has no target forward actor", clientProtobuf.id)
        } else {
            when (target) {
                Forward.PlayerActor -> {
                    val playerId = playerId
                    if (playerId != null) {
                        node.playerSharding.tell(ProtobufEnvelope(playerId, message), self)
                    } else {
                        logger.warning("try to send message to uninitialized playerId, this message will be dropped")
                    }
                }

                Forward.WorldActor -> {
                    val worldId = worldId
                    if (worldId != null) {
                        node.worldSharding.tell(ProtobufEnvelope(worldId, message), self)
                    } else {
                        logger.warning("try to send message to uninitialized worldId, this message will be dropped")
                    }
                }
            }
        }
    }
}
