package com.mikai233.tools.config

import com.mikai233.config.luban.unpackZipEntries
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object LubanPublishBundleArtifacts {
    const val BUNDLE_FILE: String = "game-config.zip"

    fun bundleBytes(): ByteArray {
        return Files.readAllBytes(requireBundlePath())
    }

    fun unpackBundle(bundle: ByteArray): Map<String, ByteArray> {
        return unpackZipEntries(bundle)
    }

    private fun requireBundlePath(): Path {
        val path = System.getProperty("gameConfigBundlePath")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(Path::of)
            ?: requireProjectRoot()
                .resolve("config")
                .resolve("build")
                .resolve("generated")
                .resolve("luban")
                .resolve("bundles")
                .resolve(BUNDLE_FILE)
        check(path.exists()) {
            "Game config bundle not found: $path. Run :config:packageLubanConfigBundle first."
        }
        return path
    }

    private fun requireProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (true) {
            if (current.resolve("settings.gradle.kts").exists()) {
                return current
            }
            val parent = current.parent ?: break
            current = parent
        }
        error("Unable to locate project root from working directory ${Path.of("").toAbsolutePath()}")
    }
}
