package com.mikai233.common.core.component

import com.mikai233.common.inject.XKoin
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.koin.core.component.KoinComponent
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

class MongoHolder(private val koin: XKoin) : KoinComponent by koin, AutoCloseable {
    val client: MongoClient = MongoClients.create()
    private val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
    fun getGameTemplate(): MongoTemplate {
        return template
    }

    override fun close() {
        client.close()
    }
}