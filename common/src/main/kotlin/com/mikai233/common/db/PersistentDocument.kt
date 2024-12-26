package com.mikai233.common.db

import org.springframework.data.mongodb.core.MongoTemplate

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/6/1
 */
class PersistentDocument(
    val status: Status,
    val block: (MongoTemplate) -> Unit
)
