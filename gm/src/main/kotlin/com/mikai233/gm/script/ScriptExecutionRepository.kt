package com.mikai233.gm.script

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.domain.Sort

class ScriptExecutionRepository(private val mongoTemplate: () -> MongoTemplate) {
    fun saveExecution(view: ScriptExecutionView, dynamicTargets: Boolean) {
        mongoTemplate().save(view.toDocument(dynamicTargets))
    }

    fun saveTarget(executionId: String, target: ScriptExecutionTargetView) {
        mongoTemplate().save(target.toDocument(executionId))
    }

    fun saveTargets(executionId: String, targets: List<ScriptExecutionTargetView>) {
        targets.forEach { saveTarget(executionId, it) }
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
