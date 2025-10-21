package com.mikai233.global.data

import com.mikai233.common.db.MemData
import com.mikai233.common.entity.WorkerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

class WorkerIdMem(private val mongoTemplate: () -> MongoTemplate) : MemData<WorkerId> {
    private val workerIdByAddr: MutableMap<String, WorkerId> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val workerIdList = template.findAll(WorkerId::class.java)
        workerIdList.forEach {
            workerIdByAddr[it.addr] = it
        }
    }

    fun workerIdFor(addr: String): WorkerId? {
        return workerIdByAddr[addr]
    }

    suspend fun newWorkerIdFor(addr: String): WorkerId {
        check(!workerIdByAddr.containsKey(addr)) { "Worker $addr is already in use" }
        var nextId: Int? = null
        if (workerIdByAddr.size < 2) {
            nextId = (workerIdByAddr.entries.maxOfOrNull { it.value.id } ?: 0) + 1
        } else {
            workerIdByAddr.entries.sortedBy { it.value.id }.windowed(2).forEach { (a, b) ->
                if (a.value.id + 1 != b.value.id) {
                    nextId = a.value.id + 1
                }
            }
        }
        if (nextId == null) {
            nextId = workerIdByAddr.entries.maxOf { it.value.id } + 1
        }
        val workerId = WorkerId(addr, nextId!!)
        val template = mongoTemplate()
        workerIdByAddr[workerId.addr] = workerId
        withContext(Dispatchers.IO) { template.insert(workerId) }
        return workerId
    }

    suspend fun delete(addr: String) {
        check(workerIdByAddr.containsKey(addr)) { "Worker $addr not found" }
        val template = mongoTemplate()
        withContext(Dispatchers.IO) { template.remove(where(WorkerId::addr).isEqualTo(addr)) }
        workerIdByAddr.remove(addr)
    }
}
