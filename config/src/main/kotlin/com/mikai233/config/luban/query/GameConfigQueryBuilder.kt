package com.mikai233.config.luban.query

import io.github.realmlabs.asteria.config.ConfigComponentBuilder
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.contribution.AsteriaContributionCatalog
import kotlin.reflect.KClass

interface GameConfigQueryBuilder {
    val name: String
    val type: KClass<out Any>
    val dependencies: Set<ConfigTableName>

    suspend fun build(snapshot: ConfigSnapshot): Any
}

@AsteriaContributionCatalog(
    contract = GameConfigQueryBuilder::class,
    packageName = "com.mikai233.config.luban.query",
    className = "GeneratedGameConfigQueryBuilderContributions",
)
object GameConfigQueryBuilders {
    val defaultBuilders: List<ConfigComponentBuilder<*>> =
        GeneratedGameConfigQueryBuilderContributions.ALL.map { it.asComponentBuilder() }
}

private fun GameConfigQueryBuilder.asComponentBuilder(): ConfigComponentBuilder<*> {
    return object : ConfigComponentBuilder<Any> {
        override val name: String = this@asComponentBuilder.name

        @Suppress("UNCHECKED_CAST")
        override val type: KClass<Any> = this@asComponentBuilder.type as KClass<Any>

        override val dependencies: Set<ConfigTableName> = this@asComponentBuilder.dependencies

        override suspend fun build(snapshot: ConfigSnapshot): Any {
            return this@asComponentBuilder.build(snapshot)
        }
    }
}
