package com.mikai233.common.config.luban

import com.mikai233.common.config.luban.validation.GameConfigValidator
import com.mikai233.common.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.config.ConfigLoader
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.DefaultConfigSnapshot

class GameConfigSnapshotLoader(
    private val delegate: ConfigLoader,
    private val componentBuilders: List<GameConfigDerivedComponentBuilder> =
        GameConfigDerivedComponents.defaultBuilders,
    private val validators: List<GameConfigValidator> = GameConfigValidators.defaultValidators,
) : ConfigLoader {
    override suspend fun load(): ConfigSnapshot {
        val snapshot = delegate.load()
        val baseTables = snapshot.tables()
        val runtimeComponents = snapshot.components().filterNot { component ->
            component is GameTables
        }
        val baseSnapshot = DefaultConfigSnapshot(
            revision = snapshot.revision,
            tables = baseTables,
            components = runtimeComponents,
        )
        val derivedComponents = componentBuilders.map { it.build(baseSnapshot) }
        val components = runtimeComponents + derivedComponents
        return DefaultConfigSnapshot(
            revision = snapshot.revision,
            tables = baseTables,
            components = components,
        ).also { validated ->
            validators.forEach { it.validate(validated) }
        }
    }
}
