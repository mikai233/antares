# Asteria Game Cluster Example

This repository is an example game cluster built on top of Asteria.

It keeps a non-trivial demo domain in place:
- `gate`: client access and gateway routing
- `player`: player shard and actor logic
- `world`: world shard, world wakeup, and broadcast examples
- `global`: shared cluster services
- `gm`: admin backend and script entrypoints
- `tools`: local bootstrap helpers for config center and generated data

The old in-repo framework layer has been stripped back. Nodes now launch through Asteria-oriented runtime wiring, while `tools` keeps a development-only helper for starting the whole cluster in one JVM.

## Requirements

- JDK 21
- MongoDB
- Zookeeper
- Gradle 9

## Local Asteria Development

`settings.gradle.kts` automatically uses a sibling checkout at `../Asteria` when it exists:

```kotlin
val localAsteria = file("../Asteria")
if (localAsteria.exists()) {
    includeBuild(localAsteria)
}
```

If you are not developing Asteria locally, remove that composite build or replace dependencies with published versions.

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

## What This Example Demonstrates

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

## Game Configuration

Game configuration is loaded through Asteria's unified config model. Runtime nodes read the current Luban publication from Zookeeper and hot reload when the publication pointer changes.

Demo tables live under `common/src/main/kotlin/com/mikai233/common/config/luban/` and cover items, monsters, drop pools, scenes, and activities.

## Persistence Example

The example keeps the existing Mongo-backed entity and memdata flow so the repository stays large enough to demonstrate real game-cluster concerns instead of only toy handlers.

## Debug Client

The protocol debug client is in `client/`. Update `client/lua/proto.lua` if your local protocol path differs, then run the client to send protobuf messages to the gate node.
