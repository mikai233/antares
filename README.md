# antares

# Akka-based Distributed Game Server

## Development Environment

- JDK21 (azul-21)
- MongoDB
- Gradle 9
- Zookeeper

## Getting Started

1. Run `ZookeeperInitializer.kt` to initialize Zookeeper data.
2. Run `GameConfigExporter.kt` to upload configuration table data to Zookeeper. The test configuration table path is `tools/src/main/resources/excel`.
3. Execute `Stardust.kt` to start the game server.

## Quick Start

### Define Protobuf Protocol

```protobuf
syntax = "proto3";

package com.mikai233.protocol;

message TestReq {

}

message TestResp {

}
```

### Assign Protocol IDs

The protocol IDs for requests and responses must match.

```protobuf
syntax = "proto3";
import "proto_system.proto";
import "proto_login.proto";
import "proto_test.proto";

package com.mikai233.protocol;

message MessageClientToServer {
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

message MessageServerToClient {
  PingResp ping_resp = 1;
  GmResp gm_resp = 2;
  TestResp test_resp = 3;
  LoginResp login_resp = 10001;
  TestNotify test_notify = 99999;
}
```

## Define `MessageHandler` to Handle Messages

A `MessageHandler` can contain any number of message handling functions, simply use the `@Handle` annotation.

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

## Start Client for Protocol Debugging

The debugging client is located in the `client` directory. Modify the `proto_path` in `client/lua/proto.lua` to the Protobuf protocol directory (usually no modification is needed as it uses a relative path).
Start `client.exe` to connect to the server. You can send data by typing the protocol name in the console. For detailed operations, refer to the `README.md` inside that directory.

## Generate Configuration Tables from Excel

The default configuration table format is as follows. The first five rows are headers: the first row is field names, the second is data types, the third is field scope (Client and Server, or Client only), and the fifth row contains comments.

| **id** | **group** | **task_id** | **condition** |    **reward**     | **point** |
|:------:|:---------:|:-----------:|:-------------:|:-----------------:|:---------:|
|  int   |    int    |     int     |      int      | vector3_array_int |    int    |
| allkey |    all    |     all     |      all      |        all        |    all    |
|        |           |             |               |                   |           |
|   id   |   Group   |   Task ID   |   Condition   |      Reward       |   Point   |
|   1    |     1     |      1      |       1       |       1,1,1       |     1     |
|   2    |     1     |      1      |       1       |       1,1,1       |     1     |
|   3    |     1     |      1      |       1       |       1,1,1       |     1     |

Run `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigGenerator.kt` to generate configuration table code based on the Excel format.

The generated code format is as follows:

```kotlin
/**
 * @param id id
 * @param group Group
 * @param taskId Task ID
 * @param condition Condition
 * @param reward Reward
 * @param point Point
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

## Exporting Configuration Table Data

### Regenerate Configuration Table Serialization Dependencies

After generating new configuration table code, you need to execute `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigImplDepsGenerator.kt` to regenerate the serialization dependencies.

### Export Binary or Upload to Zookeeper

Once the code is generated, you can parse the Excel data into the data structures and serialize them into binary. The game server will then be able to load these directly by deserializing them upon startup.

Execute `tools/src/main/kotlin/com/mikai233/tools/excel/GameConfigExporter.kt` to export the data. By default, it uploads to Zookeeper, where the server reads and deserializes the data at startup.

## Define Entity

This project uses MongoDB. An `Entity` must implement the `Entity` interface and use `@Id` for the primary key and `@Document` for the collection name. The project convention is that collection names in MongoDB should be in lowercase snake_case. An `Entity` must also contain a companion object with a no-arg static method to create a default instance.

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

## Define MemData

`TraceableMemData` provides an implementation for automatically tracking dirty data and asynchronously writing to the database. You don't need to manually save player data after modification; implementations inheriting from `TraceableMemData` will automatically track changes and periodically sync to the DB. If the data object is immutable, simply inherit from `MemData`.

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

## Execute Scripts / Hotfix Logic

Modules with a `script` directory support script execution, including both Jar and Groovy types. Jar scripts can be written in any JVM language, though they require compilation. Groovy scripts are flexible and don't need compilation, but require familiarity with Groovy and how it interacts with Kotlin.

### Writing Scripts

#### Kotlin

Can be executed within a specific Actor to query or modify player data:

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

Can be used to patch business logic:

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

### Compiling Scripts

After writing the code, run the `gradle scriptClasses` task. Once the class files are generated, refresh the Gradle tasks. You will find `buildJarForXXX` tasks under the `script` directory of the corresponding module. Executing these will build the Jar package. If the task doesn't appear, check if the class files were generated in the build directory.

### Execution

Once you have the Jar or Groovy file, you can send it to the target node or Actor for execution. The project provides a set of HTTP endpoints for script dispatch, located in `gm/src/main/kotlin/com/mikai233/gm/web/route/Script.kt`. A visual management dashboard is not yet available, so tools like Postman must be used.

For example, to execute a script in specific `PlayerActor`s, call `http://127.0.0.1:8080/script/player_actor_script` via form-data, passing `player_id` and the `script` file.

# Deployment

Run the Gradle task `gradle release` to package each node into a Jar. The resulting Jars will be collected in the `release` directory.
