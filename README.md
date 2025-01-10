# antares

# 基于Akka的分布式游戏服务器

## 开发环境

- JDK17(azul-17)
- MongoDB
- Gradle 8
- Zookeeper

## 启动

1. 运行 `ZookeeperInitializer.kt` 初始化zookeeper数据
2. 执行 `Stardust.kt` 启动游戏服务器

## 处理业务逻辑

在antares中，除了Protobuf消息外，所有的消息都继承自`Message` 接口，针对分片类型的Actor，通常会创建一个顶层的消息接口，表示此消息应该路由到哪个Actor，例如
`PlayerMessage`接口中带有一个 `id`属性，消息继承 此接口时，需要确定此消息应该传递给哪个`PlayerActor`进行处理。

需要远程发送到消息会由Kryo进行序列化和反序列化处理

### 处理客户端消息

- 注册协议

服务器和客户端采用Protobuf协议进行通信，客户端发往服务端的消息需要注册到`msg_cs.proto`这个proto里面，服务端发往客户端的消息需要注册到
`msg_sc.proto`这个proto里面，请求以Req结尾，响应以Resp结尾，并且协议号要对应，服务器单方面发送给服务端的消息以Notify结尾。

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

- 创建Handler处理客户端请求以及继承自`Message`的消息

创建一个类来处理客户端消息，通常一个功能的消息放在一个类当中，服务器启动的时候会扫描所有继承自`MessageHandler` 的类，查找其所有标有
`@Handle`的方法，注册到Map中，当客户端消息到来的时候，根据对应的Protobuf类型派发消息到对应的方法进行处理。

处理消息的类应该是`@AllOpen`的，因为这样才方便有BUG时直接继承此类覆写部分逻辑修复BUG

```kotlin
@AllOpen
class WorldLoginHandler : MessageHandler {
    @Handle
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

### 处理内部消息

处理内部消息同上，只需要把`@Handle`注解的方法的第二位换成内部消息即可。

服务器采用Netty监听客户端连接，当客户端和服务器建立连接之后，Netty会生成一个对应客户端连接的ChannelActor，此Actor是Netty和Akka的纽带，
当ChannelActor收到消息之后，就可以转发给不同的Actor处理了。

## TODO

1.
    - [ ] deploy
2.
    - [ ] eventbus
3.
    - [ ] gm management backend
