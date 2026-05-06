package com.mikai233.common.test

import com.mikai233.common.config.DataSourceConfig
import com.mikai233.common.config.MongoDeploymentMode
import com.mikai233.common.config.MongoEndpoint
import com.mikai233.common.config.mongoUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MongoDataSourceConfigTest {
    @Test
    fun shardedClusterUriIncludesRuntimeOptions() {
        val config = DataSourceConfig(
            databaseName = "game",
            mode = MongoDeploymentMode.ShardedCluster,
            endpoints = listOf(
                MongoEndpoint("mongos-0", 27017),
                MongoEndpoint("mongos-1", 27018),
            ),
            authDatabase = "admin",
            username = "antares",
            passwordEnv = "MONGO_PASSWORD",
            readPreference = "primaryPreferred",
            writeConcern = "majority",
        )

        val uri = config.mongoUri { name ->
            if (name == "MONGO_PASSWORD") {
                "secret"
            } else {
                null
            }
        }

        val expected = "mongodb://antares:secret@mongos-0:27017,mongos-1:27018/game" +
                "?authSource=admin&readPreference=primaryPreferred&w=majority"
        assertEquals(expected, uri)
    }

    @Test
    fun replicaSetModeRequiresReplicaSetName() {
        assertThrows(IllegalArgumentException::class.java) {
            DataSourceConfig(
                databaseName = "game",
                mode = MongoDeploymentMode.ReplicaSet,
                endpoints = listOf(MongoEndpoint("mongodb", 27017)),
            )
        }
    }
}
