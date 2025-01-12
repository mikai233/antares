package com.mikai233.world

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.*
import akka.cluster.Member
import akka.cluster.MemberStatus
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.core.Role
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.world.WakeupWorldReq
import com.mikai233.common.message.world.WakeupWorldResp
import kotlinx.coroutines.*
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/10
 */
class WorldWaker(private val node: WorldNode) : AbstractActor() {
    companion object {
        fun props(node: WorldNode): Props = Props.create(WorldWaker::class.java, node)
    }

    private val logger = actorLogger()
    private val cluster: Cluster = Cluster.get(context.system)
    private var totalNodes = 0
    private var upNodes = 0
    private val targetRole = Role.World.name
    private val targetPercentage = 0.7

    override fun preStart() {
        cluster.subscribe(
            self,
            initialStateAsEvents(),
            MemberEvent::class.java,
            UnreachableMember::class.java
        )
    }

    override fun postStop() {
        cluster.unsubscribe(self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(MemberUp::class.java) { memberUp ->
                handleMemberUp(memberUp.member())
            }
            .match(MemberRemoved::class.java) { memberRemoved ->
                handleMemberRemoved(memberRemoved.member())
            }
            .match(UnreachableMember::class.java) { unreachable ->
                handleMemberRemoved(unreachable.member()) // 处理不可达节点
            }
            .match(CurrentClusterState::class.java) { state ->
                // 初始化时处理当前集群状态
                totalNodes = state.members.count { it.hasRole(targetRole) }
                upNodes = state.members.count { it.hasRole(targetRole) && it.status() == MemberStatus.up() }
                checkPercentage()
            }
            .build()
    }

    private fun handleMemberUp(member: Member) {
        if (member.hasRole(targetRole)) {
            totalNodes += 1
            if (member.status() == MemberStatus.up()) {
                upNodes += 1
            }
            checkPercentage()
        }
    }

    private fun handleMemberRemoved(member: Member) {
        if (member.hasRole(targetRole)) {
            totalNodes -= 1
            if (member.status() == MemberStatus.up()) {
                upNodes -= 1
            }
            checkPercentage()
        }
    }

    private fun checkPercentage() {
        if (totalNodes > 0) {
            val percentage = upNodes.toDouble() / totalNodes
            if (percentage >= targetPercentage) {
                val worldRoleLeader = cluster.state().roleLeader(Role.World.name).get()
                if (worldRoleLeader == cluster.selfAddress()) {
                    val myself = self
                    node.coroutineScope.launch {
                        logger.info("{} start wakeup world", cluster.selfMember())
                        wakeupWorlds(myself)
                    }
                }
            }
        }
    }

    private suspend fun wakeupWorlds(myself: ActorRef) {
        var concurrency = 20
        val maxConcurrency = 100
        val minConcurrency = 1
        val growthStep = 50           // 默认增长步幅
        val shrinkStep = 10           // 默认衰减步幅
        val growthThreshold = 0.8    // 增长成功率阈值
        val shrinkThreshold = 0.5    // 衰减失败率阈值
        val cooldownPeriod = 2       // 调整并发后的冷却时间，单位：批次

        val timeout = 3.minutes
        val delayStep = when (GlobalEnv.serverMode) {
            ServerMode.DevMode -> 0.seconds
            ServerMode.ReleaseMode -> 5.seconds
        }
        val pendingWorlds = node.gameWorldMeta.worlds.toMutableList()

        var cooldownCounter = 0

        while (pendingWorlds.isNotEmpty()) {
            // 获取当前批次
            val currentBatch = pendingWorlds.take(concurrency)
            pendingWorlds.removeAll(currentBatch)

            // 处理当前批次
            val (successWorlds, failedWorlds) = coroutineScope {
                currentBatch.map { worldId ->
                    async {
                        val result = node.worldSharding.ask<WakeupWorldResp>(WakeupWorldReq(worldId), timeout)
                        worldId to result.isSuccess
                    }
                }.awaitAll().partition { it.second }
            }

            // 将失败的world重新加入队列
            pendingWorlds.addAll(failedWorlds.map { it.first })

            // 计算成功率
            val successRate = successWorlds.size.toDouble() / currentBatch.size
            val failureRate = failedWorlds.size.toDouble() / currentBatch.size

            // 冷却期内不调整并发数
            if (cooldownCounter > 0) {
                cooldownCounter--
            } else {
                // 动态调整并发数
                when {
                    successRate >= growthThreshold -> {
                        // 增长并发数（非线性公式，例如：平方根增加）
                        concurrency = (concurrency + sqrt(growthStep.toDouble()).toInt())
                            .coerceAtMost(maxConcurrency)
                        cooldownCounter = cooldownPeriod
                    }

                    failureRate >= shrinkThreshold -> {
                        // 减少并发数（更平缓的减少步幅）
                        concurrency = (concurrency - shrinkStep)
                            .coerceAtLeast(minConcurrency)
                        cooldownCounter = cooldownPeriod
                    }
                }
            }

            // 打印日志
            logger.info(
                "Batch completed. Success: {}, Failed: {}, Remaining: {}, Current Concurrency: {}",
                successWorlds.size,
                failedWorlds.size,
                pendingWorlds.size,
                concurrency
            )

            // 固定延迟
            delay(delayStep)
        }

        logger.info("All worlds have been successfully awakened.")
        myself tell PoisonPill.getInstance()
    }
}