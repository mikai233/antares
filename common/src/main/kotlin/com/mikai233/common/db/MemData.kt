package com.mikai233.common.db

import akka.actor.typed.javadsl.AbstractBehavior
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.msg.Message
import org.springframework.data.mongodb.core.MongoTemplate

interface MemData<A, M, D> where A : AbstractBehavior<M>, M : Message, D : Any {
    /**
     * do not modify actor state in this function
     * because it's exec on other thread
     */
    fun load(actor: A, mongoTemplate: MongoTemplate): D

    fun onComplete(actor: A, db: ActorDatabase, data: D)
}
