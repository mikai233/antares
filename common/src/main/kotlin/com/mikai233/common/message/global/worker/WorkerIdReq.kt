package com.mikai233.common.message.global.worker

import com.mikai233.common.message.Message

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/8
 */
data class WorkerIdReq(val addr: String) : Message

data class WorkerIdResp(val id: Int) : Message
