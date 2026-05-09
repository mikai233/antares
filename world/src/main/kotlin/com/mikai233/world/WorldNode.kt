package com.mikai233.world

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.rpc.DefaultRpcEntityIdResolver
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.common.rpc.RpcEntityIdResolver
import com.mikai233.common.runtime.*
import com.mikai233.common.runtime.module.WORLD_WAKE_TASK
import com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq
import com.mikai233.protocol.ProtoRpcWorld.WorldWakeupResp
import com.mikai233.world.generated.GeneratedWorldConfigChangeHandlers
import com.mikai233.world.generated.GeneratedWorldNodeDispatchers
import com.mikai233.world.message.HandoffWorld
import com.mikai233.world.service.WorldService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.PekkoEntityWakerModule
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.allocationStrategy
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.config.ConfigChangeDispatcher
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.id.IdGenerator
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorldNode(
    val addr: InetSocketAddress,
    override val name: String,
    nodeId: String = "world-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
    val runtimeEnv: RuntimeEnv = RuntimeEnv.fromSystem(),
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.World))
    override val services: ServiceRegistry = ServiceRegistry()

    val worldService: WorldService
        get() = patchableServices.require(WorldService::class)

    @Volatile
    private var currentState: NodeState = NodeState.Unstarted

    override val state: NodeState
        get() = currentState

    private val clusterNode = ClusterNodeBootstrap(this, addr, nodeId, config, zookeeperConnectString, sameJvm)

    val playerSharding: ActorRef
        get() = entityShard(GameEntityKinds.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(GameEntityKinds.WorldActor)

    val idGenerator: IdGenerator
        get() = services.get(IdGenerator::class)

    val protobufDispatcher = GeneratedWorldNodeDispatchers.PROTOBUF

    val configChangeDispatcher = ConfigChangeDispatcher(
        GeneratedWorldConfigChangeHandlers.ALL,
    )

    val internalDispatcher = GeneratedWorldNodeDispatchers.INTERNAL

    init {
        val patchableServices = PatchableServiceRegistry().apply {
            register(WorldService::class, WorldService())
            register(RpcEntityIdResolver::class, DefaultRpcEntityIdResolver(GameRpcProtocol.protocol))
        }
        services.register(PatchableServiceRegistry::class, patchableServices)
    }

    override suspend fun launch() {
        clusterNode.launch(
            beforeClusterModules = listOf(WorldMongoIndexModule(), clusterNode.workerIdModule()),
            afterClusterModules = listOf(worldWakerModule()),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.World)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocol.playerShardExtractor(this@WorldNode))
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                handoffMessage = HandoffWorld
                extractor(GameRpcProtocol.worldShardExtractor(this@WorldNode))
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> WorldActor.props(runtime as WorldNode) }
            }
        }
    }

    private fun updateState(newState: NodeState) {
        currentState = newState
    }

    private fun worldWakerModule(): PekkoEntityWakerModule {
        return PekkoEntityWakerModule {
            moduleName = "world-waker"
            singletonName = "worldWaker"
            coordinatorRole(GameRoles.World)
            task(WORLD_WAKE_TASK) {
                kind(GameEntityKinds.WorldActor)
                targets { runtime.gameWorldIds }
                message { worldId ->
                    WorldWakeupReq.newBuilder()
                        .setWorldId(worldId)
                        .build()
                }
                success { response ->
                    response is WorldWakeupResp
                }
                readiness {
                    role(GameRoles.World)
                    minUpRatio = 0.7
                }
                concurrency {
                    initial = 20
                    min = 1
                    max = 100
                    growthStep = 7
                    shrinkStep = 10
                    growthSuccessRate = 0.8
                    shrinkFailureRate = 0.5
                    adjustmentWindow = 20
                    cooldownWindows = 2
                }
                retry {
                    timeout = 3.minutes
                    initialDelay = when (runtimeEnv.serverMode) {
                        ServerMode.DevMode -> 1.milliseconds
                        ServerMode.ReleaseMode -> 5.seconds
                    }
                    maxDelay = initialDelay
                    backoffFactor = 1.0
                    maxAttempts = null
                    exhaustedDelay = null
                }
            }
        }
    }

}

private class Cli(runtimeEnv: RuntimeEnv) {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = runtimeEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2336

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "world.conf"

    @Parameter(names = ["-z", "--zookeeper"], description = "zookeeper")
    var zookeeper: String = runtimeEnv.zookeeperConnect

    @Parameter(names = ["-n", "--name"], description = "system name")
    var name: String = SYSTEM_NAME

    @Parameter(names = ["-i", "--node-id"], description = "runtime node id")
    var nodeId: String? = null
}

suspend fun main(args: Array<String>) {
    val runtimeEnv = RuntimeEnv.fromSystem()
    val cli = Cli(runtimeEnv)
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    WorldNode(addr, cli.name, cli.nodeId ?: "world-${cli.port}", config, cli.zookeeper, runtimeEnv = runtimeEnv).also {
        it.launch()
        it.awaitTermination()
    }
}
