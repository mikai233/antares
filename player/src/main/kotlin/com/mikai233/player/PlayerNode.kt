package com.mikai233.player

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.battle.*
import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.rpc.DefaultRpcEntityIdResolver
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.common.rpc.RpcEntityIdResolver
import com.mikai233.common.runtime.*
import com.mikai233.common.runtime.module.BattleDiscoveryModule
import com.mikai233.player.generated.GeneratedPlayerConfigChangeHandlers
import com.mikai233.player.generated.GeneratedPlayerNodeDispatchers
import com.mikai233.player.message.HandoffPlayer
import com.mikai233.player.service.ChatService
import com.mikai233.player.service.LoginService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
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

class PlayerNode(
    val addr: InetSocketAddress,
    override val name: String,
    nodeId: String = "player-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
    val runtimeEnv: RuntimeEnv = RuntimeEnv.fromSystem(),
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.Player))
    override val services: ServiceRegistry = ServiceRegistry()

    val loginService: LoginService
        get() = patchableServices.require(LoginService::class)

    val chatService: ChatService
        get() = patchableServices.require(ChatService::class)

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

    val protobufDispatcher = GeneratedPlayerNodeDispatchers.PROTOBUF

    val configChangeDispatcher = ConfigChangeDispatcher(
        handlers = GeneratedPlayerConfigChangeHandlers.ALL,
        executor = { actor, task ->
            actor.execute("config-change:${task.handler}") {
                task.run()
            }
        },
        failureHandler = { actor, failure ->
            actor.logger.error(
                failure.cause,
                "player:{} config change handler:{} failed revision:{}",
                actor.playerId,
                failure.handler,
                failure.revision.version,
            )
        },
    )

    val internalDispatcher = GeneratedPlayerNodeDispatchers.INTERNAL

    private val battleConfig = BattleConfig.load(config)
    private val battleEndpointRegistry = BattleEndpointRegistry(battleConfig.endpoints)

    init {
        val patchableServices = PatchableServiceRegistry().apply {
            register(LoginService::class, LoginService())
            register(ChatService::class, ChatService())
            register(RpcEntityIdResolver::class, DefaultRpcEntityIdResolver(GameRpcProtocol.protocol))
        }
        services.register(
            GamePatchBindings::class,
            GamePatchBindings(
                services = patchableServices,
                playerMessageRegistry = GeneratedPlayerNodeDispatchers.PROTOBUF_REGISTRY,
                playerInternalMessageRegistry = GeneratedPlayerNodeDispatchers.INTERNAL_REGISTRY,
            ),
        )
        services.register(BattleSessionRegistry::class, battleEndpointRegistry)
        services.register(
            BattleControlClient::class,
            DirectBattleControlClient(
                registry = battleEndpointRegistry,
                tokenCodec = BattleTokenCodec(battleConfig.tokenSecret),
                battleIdGenerator = { idGenerator.nextId() },
                tokenTtl = battleConfig.tokenTtl,
            ),
        )
        services.register(PatchableServiceRegistry::class, patchableServices)
        services.register(StartupLikeReloadPlan::class, PlayerGameTimeReloadPlan(this))
    }

    override suspend fun launch() {
        clusterNode.launch(
            beforeClusterModules = listOf(PlayerMongoIndexModule(), clusterNode.workerIdModule()),
            afterClusterModules = listOf(BattleDiscoveryModule(battleEndpointRegistry)),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.Player)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                handoffMessage = HandoffPlayer
                extractor(GameRpcProtocol.playerShardExtractor(this@PlayerNode))
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> PlayerActor.props(runtime as PlayerNode) }
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocol.worldShardExtractor(this@PlayerNode))
            }
        }
    }

    private fun updateState(newState: NodeState) {
        currentState = newState
    }

}

private class Cli(runtimeEnv: RuntimeEnv) {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = runtimeEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2333

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "player.conf"

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
    PlayerNode(
        addr,
        cli.name,
        cli.nodeId ?: "player-${cli.port}",
        config,
        cli.zookeeper,
        runtimeEnv = runtimeEnv,
    ).also {
        it.launch()
        it.awaitTermination()
    }
}
