# antares

# 基于 Akka 的分布式游戏服务器

## 开发环境

- JDK21(azul-21)
- MongoDB
- Gradle 8
- Zookeeper

## 启动

1. 运行 `ZookeeperInitializer.kt` 初始化zookeeper数据
2. 执行 `Stardust.kt` 启动游戏服务器

## 快速上手

### 定义 Protobuf 协议

```protobuf
syntax = "proto3";

package com.mikai233.protocol;

message TestReq{

}

message TestResp{

}
```

### 为协议分配协议号

请求和回包的协议号要一致

```protobuf
syntax = "proto3";
import "proto_system.proto";
import "proto_login.proto";
import "proto_test.proto";

package com.mikai233.protocol;

message MessageClientToServer{
  PingReq ping_req = 1;
  GmReq gm_req = 2;
  TestReq test_req = 3;
  LoginReq login_req = 10001;
}
```

```protobuf
syntax = "proto3";
import "proto_system.proto";
import "proto_login.proto";
import "proto_test.proto";

package com.mikai233.protocol;

message MessageServerToClient{
  PingResp ping_resp = 1;
  GmResp gm_resp = 2;
  TestResp test_resp = 3;
  LoginResp login_resp = 10001;
  TestNotify test_notify = 99999;
}
```

## 定义 `MessageHandler` 处理消息

`MessageHandler` 中可以包含任意多个消息处理函数，只需要使用 `@Handle` 注解即可。

```kotlin
@AllOpen
@Suppress("unused")
class TestHandler : MessageHandler {
    @Handle
    fun handleTestReq(player: PlayerActor, testReq: TestReq) {
        player.send(testResp { })
    }
}
```

## 启动客户端调试协议

调试客户端位于 client 目录，将 `client/lua/proto.lua` 中的 `proto_path` 修改为 Protobuf 协议目录（一般不用修改，已经使用相对路径定位）。
启动 `client.exe` 即可和服务端连接，在控制台中输入协议名即可发送数据，具体操作看里面的 README.md。

## 从 Excel 生成配置表

默认配置表格式如下，前五行为表头，第一行为字段名，第二行为字段的数据类型，第三行为字段的作用域（客户端和服务端、或者仅客户端），第五行为注释。

| **id** | **group** | **task_id** | **condition** |    **reward**     | **point** |
|:------:|:---------:|:-----------:|:-------------:|:-----------------:|:---------:|
|  int   |    int    |     int     |      int      | vector3_array_int |    int    |
| allkey |    all    |     all     |      all      |        all        |    all    |
|        |           |             |               |                   |           |
|   id   |    分组     |    任务id     |      条件       |        奖励         |    积分     |
|   1    |     1     |      1      |       1       |       1,1,1       |     1     |
|   2    |     1     |      1      |       1       |       1,1,1       |     1     |
|   3    |     1     |      1      |       1       |       1,1,1       |     1     |

执行 `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigGenerator.kt` 即可根据配置表格式生成配置表代码。

生成的代码格式如下：

```kotlin
/**
 * @param id id
 * @param group 分组
 * @param taskId 任务id
 * @param condition 条件
 * @param reward 奖励
 * @param point 积分
 */
data class TestConfig(
    val id: Int,
    val group: Int,
    val taskId: Int,
    val condition: Int,
    val reward: List<Triple<Int, Int, Int>>,
    val point: Int,
) : GameConfig<Int> {
    override fun id(): Int = id
}

class TestConfigs : GameConfigs<Int, TestConfig>() {
    override fun excelName(): String = "test.xlsx"

    override fun parseRow(row: Row): TestConfig {
        val id = row.parseInt("id")
        val group = row.parseInt("group")
        val taskId = row.parseInt("task_id")
        val condition = row.parseInt("condition")
        val reward = row.parseIntTripleArray("reward")
        val point = row.parseInt("point")
        return TestConfig(id, group, taskId, condition, reward, point)
    }

    override fun parseComplete(): Unit = Unit

    /**
     * TODO: Implement validation logic
     */
    override fun validate() {
    }
}
```

## 导出配置表数据

### 重新生成配置表序列化依赖

在生成新的配置表代码之后，需要执行 `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigImplDepsGenerator.kt`
重新生成配置表代码序列化依赖。

### 导出二进制或者上传到 Zookeeper

生成好配置表代码之后，就可以将 Excel 配置表的数据解析成配置表代码数据，然后将此结构序列化成二进制数据，以供游戏启动是直接反序列化此结构加载配置表数据。

执行 `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigExporter.kt` 导出配置表数据，默认上传到 Zookeeper，程序启动之后会从
Zookeeper 读取配置表数据然后反序列化。

## 定义 Entity

此项目使用的数据库是 MongoDB，`Entity` 必须继承 `Entity` 接口，并使用 `@Id`标明主键，`@Document` 标明集合名字，项目中要求
`Entity` 在 MongoDB 中的集合名为小写下划线形式（规范）。`Entity` 中必须包含一个伴生对象，里面包含一个无参的静态方法，用于创建默认的
`Entity`

```kotlin
@Document(collection = "player_abstract")
data class PlayerAbstract(
    @Id
    val playerId: Long,
    val worldId: Long,
    val account: String,
    var nickname: String,
    var level: Int,
    val createTime: Long,
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): PlayerAbstract {
            return PlayerAbstract(0, 0, "", "", 0, 0)
        }
    }
}
```

## 定义 MemData

`TraceableMemData` 为自动追踪脏数据并异步写库的实现，业务中修改玩家数据之后不用手动存库，继承自 `TraceableMemData`
的实现会自动追踪脏数据并定期写库。如数据对象不可变则直接继承 `MemData` 即可。

```kotlin
class PlayerAbstractMem(
    private val worldId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    coroutineScope: TrackingCoroutineScope,
) :
    TraceableMemData<Long, PlayerAbstract>(PlayerAbstract::class, EntityKryoPool, coroutineScope, mongoTemplate) {
    private val playerAbstracts: MutableMap<Long, PlayerAbstract> = mutableMapOf()
    private val accountToAbstracts: MutableMap<String, PlayerAbstract> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val playerAbstractList =
            template.find<PlayerAbstract>(Query.query(where(PlayerAbstract::worldId).`is`(worldId)))
        playerAbstractList.forEach {
            playerAbstracts[it.playerId] = it
            accountToAbstracts[it.account] = it
        }
    }

    override fun entities(): Map<Long, PlayerAbstract> {
        return playerAbstracts
    }

    fun addAbstract(abstract: PlayerAbstract) {
        check(playerAbstracts.containsKey(abstract.playerId).not()) { "abstract:${abstract.playerId} already exists" }
        playerAbstracts[abstract.playerId] = abstract
        accountToAbstracts[abstract.account] = abstract
    }

    fun delAbstract(playerAbstract: PlayerAbstract) {
        accountToAbstracts.remove(playerAbstract.account)
        playerAbstracts.remove(playerAbstract.playerId)
    }

    operator fun get(playerId: Long) = playerAbstracts[playerId]

    fun getByAccount(account: String) = accountToAbstracts[account]
}
```

## 执行脚本/修复逻辑

在模块下带有 `script` 目录的，可以执行脚本，支持 Jar 类型的和 Groovy 类型的脚本， Jar 类型的可以用任何 JVM
语言编写，选自己熟悉的语言就好了，缺点就是需要编译。Groovy 类型的脚本灵活，不用编译，但是需要使用者熟悉 Groovy，并且需要了解如何与
Kotlin 进行交互，才能轻松的在 Groovy 中调用项目中的 Kotlin 代码。

### 编写脚本

#### Kotlin

可以在指定 Actor 中执行，查询玩家数据或者修改玩家数据：

```kotlin
class TestPlayerScript : ActorScriptFunction<PlayerActor> {
    private val logger = logger()

    override fun invoke(player: PlayerActor, p2: ByteArray?) {
        logger.info("playerId:{} hello world", player.playerId)
        player.node.gameWorldConfigCache.forEach { (id, config) ->
            logger.info("id:{} config:{}", id, config)
        }
    }
}
```

可以修补业务逻辑：

```kotlin
class LoginServiceFix : LoginService() {
    val logger = logger()
    override fun createPlayer(player: PlayerActor, playerCreateReq: PlayerCreateReq) {
        logger.info("fix logic")
        super.createPlayer(player, playerCreateReq)
    }
}

class PlayerScriptFunction : NodeRoleScriptFunction<PlayerNode> {
    private val logger = logger()

    override fun invoke(p1: PlayerNode, p2: ByteArray?) {
        loginService = LoginServiceFix()
        logger.info("fix login service done")
    }
}
```

### Groovy

```groovy
class TestGroovyActorScript implements ActorScriptFunction<PlayerActor> {
    @Override
    Unit invoke(PlayerActor playerActor, byte[] bytes) {
        playerActor.logger.info("hello groovy")
        return null
    }
}
```

### 编译脚本

代码写好之后执行 `gradle scriptClasses` 任务，等 class 文件生成后重新刷新 Gradle 任务，在 Gradle 对应模块下的 script
目录下会生成
`buildJarForXXX` 的任务，执行就会构建 Jar 包。如果没有出现该任务，请检查 build 目录是否有生成对应的 class 文件。

### 执行

在得到 Jar 包或者 Groovy 文件之后，就可以发往目标节点或者 Actor 进行执行了。项目中提供了一套 Http 接口以供发送脚本时调用，位于
`gm/src/main/kotlin/com/mikai233/gm/web/route/Script.kt`，目前还没有提供可视化的管理后台来调用此接口，所以只能用 Postman
之类的工具进行调用。

例如我想要在某些 `PlayerActor` 中执行脚本，那么我需要调用例如 `http://127.0.0.1:8080/script/player_actor_script` 的地址，然后使用
form-data 的形式传入 `player_id` 以及 `script` 文件。

# 部署

执行 Gradle 任务 `gradle release`，即可根据每个节点打 Jar 包，打出的 Jar 包会统一拷贝到 `release` 目录下。
