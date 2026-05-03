# Antares

This repository is a game server scaffold built on top of Asteria.

It keeps a non-trivial demo domain in place:
- `gate`: client access and gateway routing
- `player`: player shard and actor logic
- `world`: world shard, world wakeup, and broadcast examples
- `global`: shared cluster services
- `gm`: admin backend and script entrypoints
- `tools`: local bootstrap helpers for config center and generated data

It is intended as a reusable starting point for real game services, not just a minimal demo. Besides core cluster wiring, the scaffold keeps patterns for the kinds of business problems that repeatedly show up in game backends, such as MongoDB historical-data compatibility and configuration-driven data evolution.

## Requirements

- JDK 21
- MongoDB
- Zookeeper
- Gradle 9

## Dependencies

The project resolves Asteria directly from Maven Central. The current dependency line targets `io.github.realm-labs.asteria:0.1.3`.

## Quick Start

1. Start MongoDB and Zookeeper.
2. Run `tools/src/main/kotlin/com/mikai233/tools/zookeeper/ZookeeperInitializer.kt`.
3. Launch `tools/src/main/kotlin/com/mikai233/tools/cluster/DevClusterLauncher.kt`.
4. Optionally start the protocol debug client in `client/`.

If you want to debug an individual node, the node mains in `global/`, `player/`, `world/`, `gate/`, and `gm/` are still available.

The bootstrap tool writes a default topology into Zookeeper:
- `player-2333`
- `player-2334`
- `world-2335`
- `global-2336`
- `gate-2337`
- `gm-2338`

It also publishes:
- demo MongoDB datasource config
- gate netty config on port `6666`
- demo world definitions
- demo Luban-style game config artifacts

## What This Scaffold Demonstrates

- Asteria-based node startup with explicit role and shard registration
- internal RPC on top of `ProtoRpc`
- explicit handler registration through `MessageDispatcher`
- actor-oriented handler contexts
- config center integration and Luban publication loading
- Mongo-backed entity and memdata patterns
- GM scripts and admin operations

## Message Handling

Handlers are registered explicitly. This project no longer uses reflection-style `@Handle` scanning.

```kotlin
private val protobufHandlers = PlayerMessageHandlerRegistry<GeneratedMessage>().apply {
    register(GmReq::class, gmReqHandler)
    register(TestReq::class, testReqHandler)
    register(PlayerLoginReq::class, playerLoginReqHandler)
}

val protobufDispatcher = MessageDispatcher(protobufHandlers)
```

Actor handlers use Asteria actor contexts directly:

```kotlin
class PlayerLoginEventHandler : PlayerMessageHandler<PlayerLoginEvent> {
    override fun handle(context: PlayerHandlerContext, message: PlayerLoginEvent) {
        val player = context.actor
        // business logic
    }
}
```

## Internal RPC

Internal protobuf RPC messages live under [proto/src/main/proto/rpc](/Users/mikai/IdeaProjects/akka-game-server/proto/src/main/proto/rpc), while client-facing protobuf messages live under [proto/src/main/proto/client](/Users/mikai/IdeaProjects/akka-game-server/proto/src/main/proto/client).

This scaffold currently uses a centralized JSON registry for internal RPC message ids:
- [proto/protocol/rpc-protocol.json](/Users/mikai/IdeaProjects/akka-game-server/proto/protocol/rpc-protocol.json)

That registry is generated from the proto descriptor set by `:proto:generateRpcProtocolRegistry`, then consumed together with the descriptor set to generate the internal protobuf RPC protocol. Entity-id extraction for shard-routed internal RPC messages still comes from proto options on the RPC messages themselves.

This is an intentional transitional model:
- ids stay centrally managed
- ids are not scattered across individual proto messages
- client/gateway routing stays separate from internal RPC registration

The desired long-term direction is to keep centralized id allocation while letting Asteria own the registry maintenance end to end, instead of generating `rpc-protocol.json` in the project.

## Game Configuration

Game configuration is loaded through Asteria's unified config model. Runtime nodes read the current Luban publication from Zookeeper and hot reload when the publication pointer changes.

Demo tables live under `common/src/main/kotlin/com/mikai233/common/config/luban/` and cover items, monsters, drop pools, scenes, and activities.

## Persistence Patterns

The scaffold keeps the Mongo-backed entity and memdata flow, including compatibility-oriented entity factory patterns for handling older persisted documents while preserving strict business constructors.

## Debug Client

The protocol debug client is in `client/`. Update `client/lua/proto.lua` if your local protocol path differs, then run the client to send protobuf messages to the gate node.
