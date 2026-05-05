package com.mikai233.tools.config

data class GameConfigPublishOptions(
    val version: String? = null,
) {
    companion object {
        fun fromEnvironment(): GameConfigPublishOptions {
            return GameConfigPublishOptions(
                version = firstNonBlank(
                    System.getProperty("gameConfigVersion"),
                    System.getenv("GAME_CONFIG_VERSION"),
                    classpathVersion(),
                ),
            )
        }

        private fun classpathVersion(): String? {
            return GameConfigPublishOptions::class.java.classLoader
                .getResourceAsStream("version")
                ?.bufferedReader()
                ?.use { it.readText().trim() }
        }

        private fun firstNonBlank(vararg values: String?): String? {
            return values.firstOrNull { !it.isNullOrBlank() }?.trim()
        }
    }
}
