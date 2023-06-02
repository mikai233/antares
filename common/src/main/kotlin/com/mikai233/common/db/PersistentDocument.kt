package com.mikai233.common.db

import org.bson.Document

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/6/1
 */
class PersistentDocument(
    val operation: Operation,
    val status: SubmitStatus,
    val document: Document?
)