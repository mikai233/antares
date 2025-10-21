package com.mikai233.global.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.Handle
import com.mikai233.common.extension.tell
import com.mikai233.common.message.MessageHandler
import com.mikai233.common.message.global.worker.WorkerIdReq
import com.mikai233.common.message.global.worker.WorkerIdResp
import com.mikai233.global.actor.WorkerActor

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/8
 */
@AllOpen
@Suppress("unused")
class WorkerHandler : MessageHandler {
    @Handle
    fun handleWorkerIdReq(actor: WorkerActor, req: WorkerIdReq) {
        val workerId = actor.uidMem.workerIdFor(req.addr)
        if (workerId != null) {
            actor.sender.tell(WorkerIdResp(workerId.id))
        } else {
            val sender = actor.sender
            actor.launch {
                val newWorkerId = actor.uidMem.newWorkerIdFor(req.addr)
                sender.tell(WorkerIdResp(newWorkerId.id))
            }
        }
    }
}
