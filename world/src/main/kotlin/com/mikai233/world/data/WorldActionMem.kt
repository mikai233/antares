package com.mikai233.world.data

import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.MemData
import com.mikai233.shared.entity.WorldAction
import com.mikai233.shared.message.WorldMessage
import com.mikai233.world.WorldActor
import org.springframework.data.mongodb.core.MongoTemplate

class WorldActionMem : MemData<WorldActor, WorldMessage, WorldAction> {
    lateinit var worldAction: WorldAction
        private set

    override fun load(actor: WorldActor, mongoTemplate: MongoTemplate): WorldAction {
        return mongoTemplate.findById(actor.worldId, WorldAction::class.java) ?: mongoTemplate.save(
            WorldAction(
                actor.worldId,
                0L,
                0L
            )
        )
    }

    override fun onComplete(actor: WorldActor, db: ActorDatabase, data: WorldAction) {
        db.traceDatabase.traceEntity(data)
        worldAction = data
    }
}