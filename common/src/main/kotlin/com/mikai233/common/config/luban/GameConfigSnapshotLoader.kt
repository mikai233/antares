package com.mikai233.common.config.luban

import com.mikai233.common.config.luban.validation.GameConfigValidator
import com.mikai233.common.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.config.ConfigLoader
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTable
import io.github.realmlabs.asteria.config.DefaultConfigSnapshot

class GameConfigSnapshotLoader(
    private val delegate: ConfigLoader,
    private val componentBuilders: List<GameConfigDerivedComponentBuilder> = GameConfigDerivedComponents.defaultBuilders,
    private val validators: List<GameConfigValidator> = GameConfigValidators.defaultValidators,
) : ConfigLoader {
    override suspend fun load(): ConfigSnapshot {
        val snapshot = delegate.load()
        val baseComponents = snapshot.components()
        val baseSnapshot = DefaultConfigSnapshot(
            revision = snapshot.revision,
            tables = baseComponents.filterIsInstance<ConfigTable<*, *>>(),
            components = baseComponents,
        )
        val derivedComponents = componentBuilders.map { it.build(baseSnapshot) }
        val components = baseComponents + derivedComponents
        return DefaultConfigSnapshot(
            revision = snapshot.revision,
            tables = components.filterIsInstance<ConfigTable<*, *>>(),
            components = components,
        ).also { validated ->
            validators.forEach { it.validate(validated) }
        }
    }
}
