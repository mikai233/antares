package com.mikai233.tools.config

import com.mikai233.config.luban.GeneratedLubanMetadata
import com.mikai233.config.luban.unpackZipEntries
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationArtifact
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists

object LubanPublishBundleArtifacts {
    // Packaging-only helper for publishing generated Luban binaries to the external config flow.
    const val BUNDLE_FILE: String = "game-config.zip"

    fun bundleArtifact(): ConfigPublicationArtifact {
        return ConfigPublicationArtifact(BUNDLE_FILE, bundleBytes())
    }

    fun bundleBytes(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                rawBytesByPath().toSortedMap().forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    fun unpackBundle(bundle: ByteArray): Map<String, ByteArray> {
        return unpackZipEntries(bundle)
    }

    private fun rawBytesByPath(): Map<String, ByteArray> {
        return GeneratedLubanMetadata.files.associateWith(::requireBytes)
    }

    private fun requireBytes(file: String): ByteArray {
        return Files.readAllBytes(requireGeneratedDataDir().resolve(file))
    }

    private fun requireGeneratedDataDir(): Path {
        val dir = requireProjectRoot()
            .resolve("config")
            .resolve("build")
            .resolve("generated")
            .resolve("luban")
            .resolve("resources")
            .resolve("luban")
        check(dir.exists()) {
            "Generated Luban data directory not found: $dir. Run :config:refreshLubanConfig first."
        }
        return dir
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
