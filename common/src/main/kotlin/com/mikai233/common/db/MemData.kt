package com.mikai233.common.db

import akka.actor.AbstractActor
import org.springframework.data.mongodb.core.MongoTemplate

interface MemData<A, D> where A : AbstractActor, D : Any {
    /**
     * do not modify actor state in this function
     * because it's exec on other thread
     */
    fun load(actor: A, mongoTemplate: MongoTemplate): D

    fun onComplete(actor: A, db: ActorDatabase, data: D)
}
