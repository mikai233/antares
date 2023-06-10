# antares

# distributed game server based on akka

## development environment

- JDK17(azul-17)
- MongoDB
- Gradle 8
- Zookeeper

## basic demo

1. run  ```NodeConfigInit.kt``` and ```GameWorldConfigInit.kt``` init zookeeper
2. run ``` gradlew bootJar ``` to build fatjar
3. run ```java -jar stardust-1.0.0.jar``` to start the all-in-one server

## handle logic

in antares, all messages are inherit from ``Message`` interface, each actor has a top level message
interface, for example, player actor declare ``PlayerMessage`` indicate this actor accept all subtype
of ``PlayerMessage``.

message inherit from ``PlayerMessage`` is internal message which is only used by player actor locally, it
cannot send remotely,
because remote message require serializable, so we add a ``SerdePlayerMessage`` indicate that this message can be
serialized.
``SerdePlayerMessage`` also inherit from ``SerdeMessage``, message inherit from ``SerdeMessage`` support automatic
serialize and deserialize by framework(see akka kryo).

usually, we add custom logic message inherit from ``SerdePlayerMessage``, because it usually needs to be sent remotely,
so serde is required.

### client logic

- register your protobuf message in ```msg_cs.proto``` and ```msg_sc.proto```

```protobuf
syntax = "proto3";
import "proto_login.proto";

package com.mikai233.protocol;

message MessageClientToServer{
  LoginReq login_req = 10001;
}
```

```protobuf
syntax = "proto3";
import "proto_login.proto";

package com.mikai233.protocol;

message MessageServerToClient{
  LoginResp login_resp = 10001;
}
```

- create handler class in handler package to handle your logic

```kotlin
@AllOpen
class WorldLoginHandler : MessageHandler {
    fun handlePlayerLogin(world: WorldActor, playerLogin: PlayerLogin) {
        val loginReq = playerLogin.req
        val channelActor = playerLogin.channelActor
        //generate playerId
        val session = world.sessionManager.createOrUpdateSession(Random.nextUInt().toLong(), channelActor)
        val playerId = Random.nextUInt().toLong()
        session.writeProtobuf(loginResp {
            playerData = playerData {
                this.playerId = playerId
                nickname = "mikai233"
            }
            result = ProtoLogin.LoginResult.Success
        })
        world.playerActor.tell(shardingEnvelope("$playerId", WHPlayerLogin("mikai233", playerId, world.worldId)))
    }
}
```

client message through the netty send to channel actor, and channel actor send shard message to player actor
or world actor, It depends on you. parameters of handler function depends on how you pass it to the message handler

### internal logic

## TODO