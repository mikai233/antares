# Antares

Antares is a game server scaffold built on top of [Asteria](https://github.com/realm-labs/Asteria). It keeps a
non-trivial demo domain in place and focuses on the parts that usually become painful in real projects: clustered
routing, generated message dispatch, runtime patching policy, configuration publication, config reload, and
historical-data compatibility.

Main modules:

- `gate`: client access, gateway routing, topic subscription, and channel-facing handlers
- `player`: player shard, player actor logic, and player-side config-change repair
- `world`: world shard, wakeup flow, cross-world topics, and broadcast examples
- `global`: shared cluster services and singleton-style runtime pieces
- `gm`: admin backend and script entrypoints
- `proto`: client/internal protobuf contracts and generated protocol support
- `common`: shared runtime, config, routing, patching, and persistence abstractions
- `tools`: local topology/config bootstrap helpers
- `stardust`: local all-in-one development launcher
- `battle`: Rust stateful battle service prototype with direct client data-plane access

## Requirements

- JDK 21
- MongoDB
- Zookeeper
- Gradle 9

## Quick Start

1. Start MongoDB and Zookeeper.
2. Run `./gradlew :stardust:prepareLocalDev` once when local Zookeeper is empty or stale.
3. Run `./gradlew :stardust:run`.
4. Optionally start the protocol debug client in `client/`.

`stardust:run` only starts the all-in-one local development cluster. It does not publish config automatically.

If you prefer to launch Stardust from the IDE, run this manually when local runtime config needs refreshing:

```bash
./gradlew :stardust:prepareLocalDev
```

The bootstrap tool writes a default topology into Zookeeper:

- `player-2333`
- `player-2334`
- `world-2335`
- `global-2336`
- `gate-2337`
- `gm-2338`

It also publishes:

- MongoDB datasource config
- gate netty config on port `6666`
- demo world definitions
- demo game config publication

Useful local tasks:

```bash
# Prepare local Zookeeper topology/runtime config and game config for Stardust
./gradlew :stardust:prepareLocalDev

# Re-publish changed Excel data to local Zookeeper without rewriting topology
./gradlew :tools:publishLocalGameConfig

# Re-publish with an explicit config revision override
./gradlew :tools:publishLocalGameConfig -PgameConfigVersion=4.3.0

# Reinitialize local topology/runtime config after clearing Zookeeper
./gradlew :tools:initializeLocalRuntimeConfig
```

If you changed Luban schema or table structure, refresh generated sources first:

```bash
./gradlew :config:refreshLubanConfig
./gradlew :tools:publishLocalGameConfig
```

Project version is centralized in `gradle.properties` as `projectVersion`. Code package metadata, default game config
publication revision, and Docker image tags all read that value unless explicitly overridden.

Each node can also be launched directly:

- `gate/src/main/kotlin/com/mikai233/gate/GateNode.kt`
- `player/src/main/kotlin/com/mikai233/player/PlayerNode.kt`
- `world/src/main/kotlin/com/mikai233/world/WorldNode.kt`
- `global/src/main/kotlin/com/mikai233/global/GlobalNode.kt`
- `gm/src/main/kotlin/com/mikai233/gm/GmNode.kt`

## Covered Capabilities

### Cluster runtime

- Asteria-based clustered node startup with explicit role and shard registration
- player/world shard routing on top of Pekko cluster sharding
- singleton and shared-service wiring through `common`
- coordinated shutdown and per-node script support

### Generated message dispatch

Message dispatch uses generated registration artifacts:

- Asteria `@AsteriaMessageHandler` for protobuf/internal message handlers
- generated `Generated*NodeDispatchers`
- generated patchable dispatcher registries such as `PROTOBUF_REGISTRY` and `INTERNAL_REGISTRY`

This keeps node bootstrap code small and makes handler registration explicit, typed, and reviewable without runtime
reflection.

### Generated gateway routing

Gateway routing is generated from handler annotations plus project-side aggregation:

- handlers use `@AsteriaGatewayRoute`
- route metadata is generated during build
- `gate` aggregates metadata into `GeneratedGatewayRouting`

The generated route layer covers regular static cases. GM commands share one client protobuf message, so Gate resolves
the command string to the target actor type before forwarding.

### Rust battle service integration

The `battle` workspace is a Rust prototype for stateful real-time combat. The JVM cluster remains the control plane:

- client sends `BattleStartReq` through Gate to Player
- Player selects a battle endpoint, creates a short-lived token, and returns `BattleStartResp`
- client connects directly to the battle server for frame traffic
- battle instances register themselves under `/antares/battle/instances`
- Player nodes watch that path through `BattleDiscoveryModule`

This keeps the latency-sensitive battle data plane out of Gate. Static `game.battle.endpoints` is only a local fallback
for development or environments without discovered battle instances.

### Runtime patching and scripts

Patchable services live in `PatchableServiceRegistry`, and production hotfixes are installed through the GM patch flow
so patch revision, target selection, status, audit, and rollback stay in one model.

Runtime patch plugins use Asteria's `RuntimePatchPlugin` and `RuntimePatchInstallContext`. Each node registers a
module-local `GamePatchBindings` service that exposes the registries that can be replaced on that node. A player-side
handler patch looks like this:

```kotlin
class FixLoginPatch : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get<GamePatchBindings>()

        context.messageHandlers.replace(
            bindings.playerMessageRegistry,
            LoginReq::class,
            PatchedLoginHandler(),
        )
    }
}
```

Service patches use the same patch lifecycle and order:

```kotlin
context.services.replace(
    bindings.services,
    LoginService::class,
    PatchedLoginService(),
)
```

Patch implementations live in `src/patch/kotlin` and are packaged separately from the node runtime JARs. Use the
module-level `patchJars` task to build patch artifacts; each generated JAR declares its entrypoint through the
`Patch-Class` manifest attribute. For example:

```bash
./gradlew :player:patchJars
```

GM publishes patch descriptors and artifacts into the shared config center, and nodes load executable plugins from the
same store. The GM HTTP entrypoints are:

```bash
curl -X POST http://127.0.0.1:18080/gm/api/patches/publish-and-apply \
  -F 'request={
    "id":"fix-login",
    "roles":["Player"],
    "requiredRoles":["Player"]
  };type=application/json' \
  -F 'file=@player/build/libs/antares_player_LoginServiceHotfixPatch.jar'

curl -X POST http://127.0.0.1:18080/gm/api/patches/fix-login/disable
```

Use `roles` to select target nodes and `requiredRoles`, `requiredModules`, or `requiredCapabilities` to declare runtime
requirements for the patch artifact.

Internal protobuf RPC shard routing uses generated protobuf entity-id metadata.

#### Actor and node scripts

The runtime enables both:

- node scripts
- actor scripts

through Asteria's script module. Scripts are kept for diagnostics, operational commands, and short-lived development
tasks; long-lived code replacement should use the GM patch path.

## Configuration Model

### Runtime config loading

Game configuration is loaded through the project's configured publication/fetch path, not from source-tree artifacts.
Runtime nodes deserialize config snapshots, build derived query components, and run validation in the same load path.

That path goes through Asteria's `ConfigModule`, which does three things:

1. load raw tables
2. build derived snapshot-level query components
3. run validators

This matters because it means:

- local validation and runtime loading use the same rules
- a bundle that passes local checks can still fail at runtime if the server code changed, and that failure happens
  during real snapshot loading rather than much later

### Global config query components

Global config query components are built after tables are deserialized and organized by table or domain area. Query
components include:

- `ItemConfigQueries.itemsByType`
- `MonsterConfigQueries.monstersBySceneId`
- `ActivityConfigQueries.activitiesByUnlockLevel`
- `DropPoolConfigQueries.dropEntriesByItemId`

Query builders live under `config/src/main/kotlin/com/mikai233/config/luban/query` and are auto-collected through
`@AsteriaContribution`.

### Actor memory repair after config reload

`ConfigChangeHandler` is not used to build global config caches. Its job is to repair actor-local memory after a config
publication changes.

The flow is:

1. config publication changes
2. `GameConfigModule` loads and validates the new `ConfigSnapshot`
3. derived query components are rebuilt for the snapshot
4. a `GameConfigChangedEvent` is published on the local ActorSystem event stream
5. player/world actors receive the event through normal internal message handlers
6. `ConfigChangeDispatcher.dispatchIfNew` compares the actor's `ActorConfigSyncMem` revision
7. matching `ConfigChangeHandler` tasks are submitted through `ConfigChangeExecutor`
8. the executor posts each handler to `actor.execute(...)`, so repair runs as normal actor mailbox work
9. handler failures are reported through the dispatcher's failure handler

Actors also catch up when they become active. Player login and world activation read the current `ConfigSnapshot` from
`ConfigService` and call `dispatchIfNew(actor, snapshot, sync)`, so handlers remain small, idempotent
`handle(actor, snapshot)` implementations.

Example: `PlayerActivityConfigChangeHandler` watches `TbActivity`, reads the player level from actor memory, and
reconciles `PlayerActivityMem` from the active table rows.

Player/world config-change handlers are also auto-collected during build through Asteria's
`@AsteriaConfigChangeHandler` annotation.

### Config validation

Validation is organized by business area. Validators live under:

- `config/src/main/kotlin/com/mikai233/config/luban/validation`

Add validators by table or domain area. Validators are auto-collected through Asteria contribution generation and
passed to `ConfigModule` as `GameConfigValidators.defaultValidators`.

### Adjustable game time

Business code should read time through actor-level `GameTime`, not direct system time. `GameTime` implements Kotlin
`Clock`, carries the configured time zone, and exposes helpers such as `nowLocal()` and `today()`.

The effective time offset is:

- global offset from config center
- plus optional actor-local offset

When the global offset changes, `GameTimeReloadModule` applies it and runs the node's `StartupLikeReloadPlan`. Player
and World nodes stop local active actors so they restart through the same path as node startup; sharded actors are not
eagerly pulled up beyond the node's normal wakeup behavior.

## Luban Pipeline

Excel is the source of truth for demo game config. The default Excel directory is configured by `lubanDataDir` in the
root `gradle.properties`.

- Excel source: `config/luban/Datas`
- generated Java table/model code: `config/src/generated/luban/java`
- generated Kotlin Luban metadata/bridge: `config/src/generated/luban/kotlin`
- generated table accessors: `config/build/generated/ksp/main/kotlin`
- generated raw `.bytes`: `config/build/generated/luban/resources/luban`
- packaged publication artifact: `config/build/generated/luban/bundles/game-config.zip`

The generated Java/Kotlin source is checked into the repo. Raw `.bytes` files are local build outputs and are not part
of the runtime classpath. Runtime nodes consume a single `game-config.zip` publication artifact from the config center
and unpack it in memory before loading Luban tables. Each bundle includes
`META-INF/antares/game-config.properties`, which defines the config publication revision.

Common commands:

```bash
# Export Luban Java code and raw .bytes
./gradlew :config:exportLubanConfig

# Export Luban Java/.bytes and refresh the generated Kotlin metadata/bridge
./gradlew :config:refreshLubanConfig

# Validate tables and custom business rules
./gradlew :config:validateLubanConfigTables

# Validate that derived game config query components can be constructed
./gradlew :config:validateLubanConfigQueries

# Package a server-consumable config bundle
./gradlew :config:packageLubanConfigBundle
```

Common configuration:

```properties
lubanDataDir=config/luban/Datas
# lubanToolRoot=/path/to/luban/tool/root
```

Use Gradle properties or environment variables to override local paths: `-PlubanDataDir=/path/to/Datas`,
`LUBAN_DATA_DIR=/path/to/Datas`, `-PlubanToolRoot=/path/to/luban/tool/root`, or
`LUBAN_TOOL_ROOT=/path/to/luban/tool/root`.

For more detail, see [config/luban/README.md](config/luban/README.md).

## Internal RPC

Internal protobuf RPC messages live under:

- `proto/src/main/proto/rpc`

Client-facing protobuf messages live under:

- `proto/src/main/proto/client`

The project keeps centralized internal RPC id allocation in:

- `proto/protocol/rpc-protocol.json`

That registry is generated from the proto descriptor set and then consumed by Asteria-side protocol generation.
Entity-id extraction for shard-routed internal RPC messages comes from protobuf metadata.

## Persistence Patterns

The scaffold keeps Mongo-backed entity and memdata flow, including compatibility-oriented patterns for loading
historical documents while preserving strict business constructors.

## Debug Client

The protocol debug client lives in `client/`. Update `client/lua/proto.lua` if your local protocol path differs, then
run the client to send protobuf messages to the gate node.
