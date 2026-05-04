# Antares

Antares is a game server scaffold built on top of Asteria. It keeps a non-trivial demo domain in place and focuses on
the parts that usually become painful in real projects: clustered routing, generated message dispatch, scriptable
hotfixes, configuration publication, config reload, and historical-data compatibility.

Main modules:

- `gate`: client access, gateway routing, topic subscription, and channel-facing handlers
- `player`: player shard, player actor logic, and player-side config-change repair
- `world`: world shard, wakeup flow, cross-world topics, and broadcast examples
- `global`: shared cluster services and singleton-style runtime pieces
- `gm`: admin backend and script entrypoints
- `proto`: client/internal protobuf contracts and generated protocol support
- `common`: shared runtime, config, routing, hotfix, and persistence abstractions
- `tools`: local topology/config bootstrap helpers

## Requirements

- JDK 21
- MongoDB
- Zookeeper
- Gradle 9

## Quick Start

1. Start MongoDB and Zookeeper.
2. Run `tools/src/main/kotlin/com/mikai233/tools/zookeeper/ZookeeperInitializer.kt`.
3. Launch `tools/src/main/kotlin/com/mikai233/tools/cluster/DevClusterLauncher.kt`.
4. Optionally start the protocol debug client in `client/`.

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

If you prefer to debug a single process, each node still has its own `main` entry:

- `gate/.../GateNode.kt`
- `player/.../PlayerNode.kt`
- `world/.../WorldNode.kt`
- `global/.../GlobalNode.kt`
- `gm/.../GmNode.kt`

## Covered Capabilities

### Cluster runtime

- Asteria-based clustered node startup with explicit role and shard registration
- player/world shard routing on top of Pekko cluster sharding
- singleton and shared-service wiring through `common`
- coordinated shutdown and per-node script support

### Generated message dispatch

Message handlers are no longer registered by hand. The project uses:

- Asteria `@AsteriaMessageHandler` for protobuf/internal message handlers
- generated `Generated*NodeDispatchers`
- generated `Generated*MessageCatalog`

This keeps node bootstrap code small and makes handler registration explicit, typed, and reviewable without runtime
reflection.

### Generated gateway routing

Gateway routing is generated from handler annotations plus project-side aggregation:

- handlers use `@AsteriaGatewayRoute`
- route metadata is generated during build
- `gate` aggregates metadata into `GeneratedGatewayRouting`

The generated route layer covers the regular static cases, while the project still keeps a small manual fallback path
for conditional business routes that are not worth forcing into the generic model.

### Scriptable hotfixes

The scaffold already covers several hotfix patterns.

#### Service hotfix

Patchable services live in `PatchableServiceRegistry` and can be replaced from node scripts.

Typical example:

```kotlin
context.runtime.replacePatchableService(
    LoginService::class,
    LoginServiceHotfix(),
    patchId = "script:login-service-hotfix",
)
```

This is the preferred way to hotfix service-level behavior without restarting the node.

#### Routing hotfix

Internal protobuf RPC shard routing normally relies on generated protobuf entity-id metadata. For the rare case where a
message is missing that declaration, the project provides a scriptable override layer through `RpcEntityIdResolver`.

Node scripts can install temporary field-based overrides, for example:

```kotlin
context.runtime.installRpcEntityIdFieldOverrides(
    worldFieldOverrides = mapOf(
        "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
    ),
)
```

This gives a production hotfix path for routing mistakes without adding a dedicated config-center format just for rare
emergency cases.

#### Actor and node scripts

The runtime enables both:

- node scripts
- actor scripts

through Asteria's script module. The project keeps script templates for both service hotfixes and routing hotfixes so
the common operational patterns stay concrete.

## Configuration Model

### Runtime config loading

Game configuration is loaded through the project's configured publication/fetch path, not from source-tree artifacts.
Runtime nodes deserialize config snapshots, build derived query components, and run validation in the same load path.

That path currently goes through `GameConfigSnapshotLoader`, which does three things:

1. load raw tables
2. build derived snapshot-level components such as `GameConfigQueries`
3. run validators

This matters because it means:

- local validation and runtime loading use the same rules
- a bundle that passes local checks can still fail at runtime if the server code changed, and that failure happens
  during real snapshot loading rather than much later

### Global config queries

`GameConfigQueries` is the global query layer built after tables are deserialized. It is for shared, snapshot-level
lookup structures such as:

- `itemsByType`
- `monstersBySceneId`
- `activitiesByUnlockLevel`
- `dropEntriesByItemId`

This is intentionally different from actor-local config repair logic.

### Actor memory repair after config reload

`ConfigChangeHandler` is not used to build global config caches. Its job is to repair actor-local memory after a config
publication changes.

The flow is:

1. config publication changes
2. runtime loads a new `ConfigSnapshot`
3. actor-side `ConfigChangeDispatcher` finds the handlers watching the changed tables
4. those handlers update actor memory

Handlers can also implement `catchUp(actor, snapshot)` so an actor can rebuild its relevant derived state when it starts
or resynchronizes.

Player/world config-change handlers are also auto-collected during build through the project-side
`@GameConfigChangeHandler` annotation.

### Config validation

Validation is split by business area instead of being kept in one large file. Current validators live under:

- `common/src/main/kotlin/com/mikai233/common/config/luban/validation`

The model is intended to scale by adding more validators per table or per domain area rather than accumulating one
monolithic rules file.

## Luban Pipeline

Excel is the source of truth for demo game config.

- Excel source: `config/luban/Datas`
- generated Java table/model code: `common/src/generated/luban/java`
- generated Kotlin bridge: `common/src/generated/luban/kotlin`
- generated raw `.bytes`: `common/build/generated/luban/resources/luban`
- packaged server bundle: `common/build/generated/luban/bundles/game-config.zip`

The generated Java/Kotlin source is checked into the repo. Raw `.bytes` files are local build outputs and are not part
of the runtime classpath.

Common commands:

```bash
# Export Luban Java code and raw .bytes
./gradlew :common:exportLubanConfig

# Export Luban Java/.bytes and refresh the generated Kotlin bridge
./gradlew :common:refreshLubanConfig

# Validate tables and custom business rules
./gradlew :common:validateLubanConfigTables

# Validate that derived GameConfigQueries can be constructed
./gradlew :common:validateLubanConfigQueries

# Package a server-consumable config bundle
./gradlew :common:packageLubanConfigBundle
```

Optional parameters:

```bash
-PlubanDataDir=/path/to/Datas
-PlubanBundleOutputDir=dist/config
```

For more detail, see [config/luban/README.md](config/luban/README.md).

## Internal RPC

Internal protobuf RPC messages live under:

- `proto/src/main/proto/rpc`

Client-facing protobuf messages live under:

- `proto/src/main/proto/client`

The project keeps centralized internal RPC id allocation in:

- `proto/protocol/rpc-protocol.json`

That registry is generated from the proto descriptor set and then consumed by Asteria-side protocol generation.
Entity-id extraction for shard-routed internal RPC messages still comes from protobuf metadata, with the additional
project-side hotfix layer described above.

## Persistence Patterns

The scaffold keeps Mongo-backed entity and memdata flow, including compatibility-oriented patterns for loading
historical documents while preserving strict business constructors. That is one of the main reasons this project is kept
as a richer scaffold instead of a minimal sample.

## Debug Client

The protocol debug client lives in `client/`. Update `client/lua/proto.lua` if your local protocol path differs, then
run the client to send protobuf messages to the gate node.
