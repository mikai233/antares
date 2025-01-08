# antares

# 基于Akka的分布式游戏服务器

## 开发环境

- JDK17(azul-17)
- MongoDB
- Gradle 8
- Zookeeper

## 最基本的demo

1. 运行  `NodeConfigInit.kt` 和 `GameWorldConfigInit.kt` 初始化zookeeper数据
2. 运行 `ExcelExporter` 导出配置表到zookeeper， excel路径：`tools/src/main/resources/excel`
3. 运行 `gradlew bootJar` 打fatjar
4. 运行 `java -jar stardust-1.0.0.jar` 启动游戏服务器

## 处理业务逻辑

在antares中，所有的消息都继承自`Message`
接口，针对每个类型的Actor，创建了一个顶层的消息接口，表示此Actor处理这一类的消息，例如`PlayerActor`
接受所有继承自`PlayerMessage`接口的消息。

在`PlayerMessage`下面会有一个名叫`SerdePlayerMessage`的子接口，这个接口同时继承了`SerdeMessage`，继承自`SerdeMessage`
的消息表示可以远程发送，否则不能跨JVM发送，因为远程发送消息需要对象可以序列化和反序列化，这里使用了akka kryo进行统一处理序列化。

业务类型的消息一般都是继承自`SerdePlayerMessage`的，因为业务层面上的大多消息都是需要跨节点和其它Actor进行通信的。
所以直接继承这个接口就好，即使这个消息不会发到远程，也没关系。

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

- 创建Handler处理客户端请求

创建一个类来处理客户端消息，通常一个功能的消息放在一个类当中，服务器启动的时候会扫描所有继承自`MessageHandler`
的类，查找其所有的方法，查看方法
的参数是否带有Protobuf消息，如果有则注册到Map中，当客户端消息到来的时候，根据对应的Protobuf类型派发消息到对应的方法进行处理。

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

服务器采用Netty监听客户端连接，当客户端和服务器建立连接之后，Netty会生成一个对应客户端连接的ChannelActor，此Actor是Netty和Akka的纽带，
当ChannelActor收到消息之后，就可以转发给不同的Actor处理了。

### 处理内部消息

如果内部消息需要跨节点发送，比如两个通信的Actor在不同的机器上，那么要继承`SerdeMessage`，如果不需要跨节点发送，比如只是Actor发了个消息给自己，
那么不需要继承`SerdeMessage`，不过通常不用区分这么细，直接继承`SerdeMessage`
就好了，通常会再封装一层，会有一个叫`BusinessMessage`的接口，
这个接口继承自`SerdeMessage`，业务消息直接继承这个接口就好了，消息的处理方式和Protobuf类似，服务器启动的时候会去扫描方法参数，注册对应的消息
Map，例子参见`com.mikai233.player.component.PlayerActorDispatchers`。

## TODO

1.
    - [ ] deploy
2.
    - [ ] eventbus
3.
    - [ ] gm management backend
