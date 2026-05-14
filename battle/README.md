# Battle Service

The battle service is a Rust runtime for stateful real-time battles.

The JVM cluster remains the control plane:

- Player/World actors create and authorize battles.
- Player returns a battle endpoint and short-lived token to the client.
- Clients connect directly to the battle server for real-time frames.
- Battle servers own in-memory battle sessions and publish final results back to the JVM side.

Current first slice:

- client-facing protobuf frames live in `client-proto/src/main/proto/client/proto_battle.proto`
- Player handles `BattleStartReq` and returns `BattleStartResp(endpoint, token)`
- JVM-side abstractions live under `common.battle`
- `battle-server` accepts length-delimited protobuf frames over TCP, validates the battle token, and returns `BattleFrameNotify`
- `battle-server` can register itself under ZooKeeper path `/antares/battle/instances`
- the Rust workspace is kept independent from Gradle so the service can evolve as a standalone binary

For local testing:

```bash
cargo run -p battle-server -- \
  --bind 127.0.0.1:7001 \
  --token-secret local-dev-battle-secret \
  --zookeeper 127.0.0.1:2181 \
  --instance-id local-battle \
  --public-host 127.0.0.1
```

The server registers an ephemeral JSON node like:

```text
/antares/battle/instances/local-battle
```

Player nodes watch that path and use discovered `UP` instances for `BattleStartReq`. Static
`game.battle.endpoints` remains a local fallback when no dynamic instances are discovered.

The lowest-latency path is:

```text
Client -> Gate -> Player       # BattleStartReq control plane
Player -> Client               # BattleStartResp(endpoint, token)
Client <-> Battle              # BattleFrameReq/BattleFrameNotify data plane
Battle -> JVM                  # final settlement, not implemented yet
```
