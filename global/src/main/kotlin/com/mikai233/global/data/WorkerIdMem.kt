package com.mikai233.global.data

import com.mikai233.common.entity.WorkerId
import io.github.mikai233.asteria.persistence.MemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

class WorkerIdMem(private val mongoTemplate: () -> MongoTemplate) : MemData {
    private val workerIdByAddr: MutableMap<String, WorkerId> = mutableMapOf()

    override suspend fun load() {
        val template = mongoTemplate()
        val workerIdList = template.findAll(WorkerId::class.java)
        workerIdList.forEach {
            workerIdByAddr[it.id] = it
        }
    }

    fun workerIdFor(addr: String): WorkerId? {
        return workerIdByAddr[addr]
    }

    suspend fun newWorkerIdFor(addr: String): WorkerId {
        check(!workerIdByAddr.containsKey(addr)) { "Worker $addr is already in use" }
        var nextId: Int? = null
        if (workerIdByAddr.size < 2) {
            nextId = (workerIdByAddr.entries.maxOfOrNull { it.value.workerId } ?: 0) + 1
        } else {
            workerIdByAddr.entries.sortedBy { it.value.workerId }.windowed(2).forEach { (a, b) ->
                if (a.value.workerId + 1 != b.value.workerId) {
                    nextId = a.value.workerId + 1
                }
            }
        }
        if (nextId == null) {
            nextId = workerIdByAddr.entries.maxOf { it.value.workerId } + 1
        }
        val workerId = WorkerId(addr, nextId)
        val template = mongoTemplate()
        workerIdByAddr[workerId.id] = workerId
        withContext(Dispatchers.IO) { template.insert(workerId) }
        return workerId
    }

    suspend fun delete(addr: String) {
        check(workerIdByAddr.containsKey(addr)) { "Worker $addr not found" }
        val template = mongoTemplate()
        withContext(Dispatchers.IO) { template.remove(where(WorkerId::id).isEqualTo(addr)) }
        workerIdByAddr.remove(addr)
    }
}
