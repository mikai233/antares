package com.mikai233.gm.script

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.FindAndReplaceOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

class ScriptExecutionRepository(private val mongoTemplate: () -> MongoTemplate) {
    fun saveExecution(view: ScriptExecutionView, dynamicTargets: Boolean) {
        mongoTemplate().save(view.toDocument(dynamicTargets))
    }

    fun saveExecution(document: ScriptExecutionDocument) {
        mongoTemplate().save(document)
    }

    fun saveExecutions(documents: List<ScriptExecutionDocument>) {
        if (documents.isEmpty()) {
            return
        }
        val operations = mongoTemplate().bulkOps(BulkOperations.BulkMode.UNORDERED, ScriptExecutionDocument::class.java)
        val options = FindAndReplaceOptions.options().upsert()
        documents.forEach { document ->
            val query = Query.query(Criteria.where(ScriptExecutionDocument::id.name).`is`(document.id))
            operations.replaceOne(query, document, options)
        }
        operations.execute()
    }

    fun saveTargets(executionId: String, targets: List<ScriptExecutionTargetView>) {
        saveTargetDocuments(targets.map { it.toDocument(executionId) })
    }

    fun saveTargetDocuments(documents: List<ScriptExecutionTargetDocument>) {
        if (documents.isEmpty()) {
            return
        }
        val operations = mongoTemplate()
            .bulkOps(BulkOperations.BulkMode.UNORDERED, ScriptExecutionTargetDocument::class.java)
        val options = FindAndReplaceOptions.options().upsert()
        documents.forEach { document ->
            val query = Query.query(Criteria.where(ScriptExecutionTargetDocument::id.name).`is`(document.id))
            operations.replaceOne(query, document, options)
        }
        operations.execute()
    }

    fun findById(id: String): ScriptExecutionDocument? {
        return mongoTemplate().findById<ScriptExecutionDocument>(id)
    }

    fun findRecent(limit: Int): List<ScriptExecutionDocument> {
        val query = Query()
            .with(Sort.by(Sort.Direction.DESC, ScriptExecutionDocument::createdAt.name))
            .limit(limit)
        return mongoTemplate().find(query, ScriptExecutionDocument::class.java)
    }

    fun findTargets(executionId: String): List<ScriptExecutionTargetDocument> {
        val query = Query.query(Criteria.where(ScriptExecutionTargetDocument::executionId.name).`is`(executionId))
            .with(Sort.by(Sort.Direction.ASC, ScriptExecutionTargetDocument::target.name))
        return mongoTemplate().find(query, ScriptExecutionTargetDocument::class.java)
    }

    fun findTargets(executionIds: Collection<String>): Map<String, List<ScriptExecutionTargetDocument>> {
        if (executionIds.isEmpty()) {
            return emptyMap()
        }
        val query = Query.query(Criteria.where(ScriptExecutionTargetDocument::executionId.name).`in`(executionIds))
            .with(Sort.by(Sort.Direction.ASC, ScriptExecutionTargetDocument::target.name))
        return mongoTemplate()
            .find(query, ScriptExecutionTargetDocument::class.java)
            .groupBy { it.executionId }
    }
}
