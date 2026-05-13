package com.mikai233.config.luban

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Properties

const val GAME_CONFIG_BUNDLE_METADATA_PATH: String = "META-INF/antares/game-config.properties"

data class GameConfigBundleMetadata(
    val version: String,
)

fun gameConfigBundleMetadata(entries: Map<String, ByteArray>): GameConfigBundleMetadata {
    val bytes = entries[GAME_CONFIG_BUNDLE_METADATA_PATH]
        ?: error("game config bundle metadata not found: $GAME_CONFIG_BUNDLE_METADATA_PATH")
    val properties = Properties().also { props ->
        ByteArrayInputStream(bytes).use(props::load)
    }
    return GameConfigBundleMetadata(
        version = properties.required("version"),
    )
}

fun encodeGameConfigBundleMetadata(version: String): ByteArray {
    require(version.isNotBlank()) { "game config bundle version must not be blank" }
    require('\n' !in version && '\r' !in version) { "game config bundle version must be single line" }
    return "version=$version\n".toByteArray(StandardCharsets.UTF_8)
}

private fun Properties.required(key: String): String {
    return getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("game config bundle metadata $key must not be blank")
}
