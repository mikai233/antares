package com.mikai233.common.db.tracked

import com.mongodb.bulk.BulkWriteResult
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate

class MongoPendingWriteFlusher(
    private val queue: PendingWriteQueue,
    private val mongoTemplate: () -> MongoTemplate,
    private val idField: String = "_id",
) {
    fun flush(): List<BulkWriteResult> {
        val writes = queue.drain()
        if (writes.isEmpty()) {
            return emptyList()
        }
        val writesBySlot = writes.groupBy { it.key.slot }
        val successfulSlots = mutableSetOf<String>()
        val results = mutableListOf<BulkWriteResult>()

        try {
            writesBySlot.forEach { (slot, slotWrites) ->
                val bulk = mongoTemplate().bulkOps(BulkOperations.BulkMode.UNORDERED, slot)
                slotWrites.forEach { write ->
                    if (write.sets.isEmpty() && write.incs.isEmpty()) {
                        bulk.updateOne(write.query(idField), write.update())
                    } else {
                        bulk.upsert(write.query(idField), write.update())
                    }
                }
                results += bulk.execute()
                successfulSlots += slot
            }
            return results
        } catch (throwable: Throwable) {
            val notFlushed = writesBySlot
                .filterKeys { slot -> slot !in successfulSlots }
                .values
                .flatten()
            queue.requeue(notFlushed)
            throw throwable
        }
    }
}
