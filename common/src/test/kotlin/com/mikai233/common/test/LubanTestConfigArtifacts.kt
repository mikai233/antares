package com.mikai233.common.test

import com.mikai233.common.config.luban.GeneratedLubanMetadata
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationArtifact
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        return LinkedHashMap<String, ByteArray>().also { filesByPath ->
            ZipInputStream(ByteArrayInputStream(bundle)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        filesByPath[entry.name] = zip.readAllBytes()
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun rawBytesByPath(): Map<String, ByteArray> {
        return GeneratedLubanMetadata.files.associateWith(::requireBytes)
    }

    private fun requireBytes(file: String): ByteArray {
        return Files.readAllBytes(requireGeneratedDataDir().resolve(file))
    }

    private fun requireGeneratedDataDir(): Path {
        val commonProjectDir = checkNotNull(System.getProperty("common.projectDir")) {
            "Missing test system property: common.projectDir"
        }
        val dir = Path.of(commonProjectDir, "src", "generated", "luban", "resources", "luban")
        check(dir.exists()) {
            "Generated Luban data directory not found: $dir. Run :common:refreshLubanConfig first."
        }
        return dir
    }
}
