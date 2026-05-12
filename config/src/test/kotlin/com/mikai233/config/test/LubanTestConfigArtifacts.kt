package com.mikai233.config.test

import com.mikai233.config.luban.GeneratedLubanMetadata
import com.mikai233.config.luban.unpackZipEntries
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationArtifact
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists

object LubanTestConfigArtifacts {
    // Test-side helper: build the publication bundle from checked-in generated binary exports.
    const val BUNDLE_FILE: String = "game-config.zip"

    fun artifacts(): List<ConfigPublicationArtifact> {
        return listOf(bundleArtifact())
    }

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
        val commonBuildDir = checkNotNull(System.getProperty("config.buildDir")) {
            "Missing test system property: config.buildDir"
        }
        val dir = Path.of(commonBuildDir, "generated", "luban", "resources", "luban")
        assumeTrue(dir.exists(), "Generated Luban data directory not found: $dir. Run :config:exportLubanConfig first.")
        return dir
    }
}
