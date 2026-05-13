package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.config.luban.GameTables
import com.mikai233.config.luban.GameTablesSnapshotBridge
import com.mikai233.config.luban.gameConfigBundleMetadata
import com.mikai233.config.luban.query.GameConfigQueryBuilders
import com.mikai233.config.luban.unpackZipEntries
import com.mikai233.config.luban.validation.GameConfigValidators
import io.github.realmlabs.asteria.cluster.config.ClusterConfigControlService
import io.github.realmlabs.asteria.cluster.config.ClusterConfigReloadResult
import io.github.realmlabs.asteria.cluster.config.ClusterConfigReloadTarget
import io.github.realmlabs.asteria.config.ConfigRevision
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.LubanBinaryLoadReport
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.publisher.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

@AllOpen
@RestController
@RequestMapping("/gm/api/config/publications")
class ConfigPublicationController(
    private val store: ConfigStore,
    private val clusterControl: ClusterConfigControlService? = null,
) {
    private val layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION)
    private val operations = ConfigPublicationOperations(store, layout)

    @GetMapping("/current")
    suspend fun current(): ConfigPublicationResponse? {
        val current = operations.current() ?: return null
        val manifest = operations.manifest(current.revision)
        return manifest.toResponse(current = true, publishedAt = current.publishedAt)
    }

    @GetMapping
    suspend fun history(): List<ConfigPublicationResponse> {
        val currentRevision = operations.current()?.revision
        return operations.history().map { record ->
            val manifest = operations.manifest(record.revision)
            manifest.toResponse(
                current = record.revision == currentRevision,
                publishedAt = record.publishedAt,
                record = record,
            )
        }
    }

    @PostMapping("/validate")
    suspend fun validate(
        @RequestParam file: MultipartFile,
    ): ConfigPublicationValidationResponse {
        val bytes = readConfigZip(file)
        val snapshot = loadSnapshot(bytes)
        return ConfigPublicationValidationResponse(
            revision = snapshot.revision,
            tableCount = snapshot.tables().size,
            tables = snapshot.tables().map { it.name.value }.sorted(),
            componentCount = snapshot.components().size,
            artifactCount = 1,
            totalArtifactBytes = bytes.size.toLong(),
        )
    }

    @PostMapping("/publish")
    suspend fun publish(
        @RequestParam file: MultipartFile,
    ): ConfigPublicationPublishResponse {
        val publication = publishConfig(readConfigZip(file))
        return ConfigPublicationPublishResponse(
            publication = publication,
            reload = null,
        )
    }

    @PostMapping("/publish-and-reload")
    suspend fun publishAndReload(
        @RequestParam file: MultipartFile,
        @RequestParam(defaultValue = "10000") timeoutMillis: Long,
    ): ConfigPublicationPublishResponse {
        require(timeoutMillis > 0) { "timeoutMillis must be greater than zero" }
        val publication = publishConfig(readConfigZip(file))
        val control = clusterControl ?: error("cluster config control service is not configured")
        val reload = control.reload(ClusterConfigReloadTarget.All, timeoutMillis.milliseconds)
        return ConfigPublicationPublishResponse(
            publication = publication,
            reload = reload,
        )
    }

    @PostMapping("/promote")
    suspend fun promote(
        @RequestParam version: String,
        @RequestParam(required = false) checksum: String?,
    ): ConfigPublicationResponse {
        val revision = publicationRevision(version, checksum)
        val current = operations.promote(revision)
        val manifest = operations.manifest(current.revision)
        return manifest.toResponse(current = true, publishedAt = current.publishedAt)
    }

    private suspend fun publishConfig(bytes: ByteArray): ConfigPublicationResponse {
        val result = ConfigPublisher(
            loader = loader(bytes),
            artifactSource = ConfigArtifactSource {
                listOf(ConfigPublicationArtifact(CONFIG_ZIP_ARTIFACT, bytes))
            },
            store = store,
            layout = layout,
            validators = GameConfigValidators.defaultValidators,
            componentBuilders = GameConfigQueryBuilders.defaultBuilders,
        ).publish()
        return result.manifest.toResponse(current = true, publishedAt = result.manifest.generatedAt)
    }

    private suspend fun loadSnapshot(bytes: ByteArray): ConfigSnapshot {
        return ConfigService(
            loader = loader(bytes),
            validators = GameConfigValidators.defaultValidators,
            componentBuilders = GameConfigQueryBuilders.defaultBuilders,
        ).load().current
    }

    private fun loader(
        bytes: ByteArray,
    ): LubanBinaryConfigLoader<GameTables, GameTables.IByteBufLoader> {
        val entries = unpackZipEntries(bytes)
        val metadata = gameConfigBundleMetadata(entries)
        return LubanBinaryConfigLoader(
            tablesType = GameTables::class,
            dataSource = MemoryLubanDataSource(entries),
            bridge = GameTablesSnapshotBridge,
            revisionFactory = { report -> revision(report, metadata.version) },
        )
    }

    private fun revision(report: LubanBinaryLoadReport, version: String): ConfigRevision {
        return ConfigRevision(
            version = version,
            checksum = report.checksum,
        )
    }

    private suspend fun publicationRevision(version: String, checksum: String?): ConfigRevision {
        val normalizedChecksum = checksum?.takeIf { it.isNotBlank() }
        if (normalizedChecksum != null) {
            return ConfigRevision(version = version, checksum = normalizedChecksum)
        }
        return operations.history()
            .firstOrNull { it.revision.version == version }
            ?.revision
            ?: ConfigRevision(version = version)
    }

    private fun readConfigZip(file: MultipartFile): ByteArray {
        require(!file.isEmpty) { "config bundle must not be empty" }
        val filename = file.originalFilename.orEmpty()
        require(filename.isBlank() || filename.endsWith(".zip", ignoreCase = true)) {
            "config bundle must be a zip file"
        }
        return file.bytes
    }

    private fun ConfigPublicationManifest.toResponse(
        current: Boolean,
        publishedAt: Instant,
        record: ConfigPublicationRecord? = null,
    ): ConfigPublicationResponse {
        return ConfigPublicationResponse(
            revision = revision,
            generatedAt = generatedAt,
            publishedAt = publishedAt,
            manifestPath = layout.manifestPath(revision).value,
            current = current,
            artifactCount = record?.artifactCount ?: artifacts.size,
            totalArtifactBytes = record?.totalArtifactBytes ?: artifacts.sumOf { it.size },
            tables = tables,
            artifacts = artifacts,
            components = components,
        )
    }

    private companion object {
        const val CONFIG_ZIP_ARTIFACT = "game-config.zip"
    }
}

data class ConfigPublicationValidationResponse(
    val revision: ConfigRevision,
    val tableCount: Int,
    val tables: List<String>,
    val componentCount: Int,
    val artifactCount: Int,
    val totalArtifactBytes: Long,
)

data class ConfigPublicationPublishResponse(
    val publication: ConfigPublicationResponse,
    val reload: ClusterConfigReloadResult?,
)

data class ConfigPublicationResponse(
    val revision: ConfigRevision,
    val generatedAt: Instant,
    val publishedAt: Instant,
    val manifestPath: String,
    val current: Boolean,
    val artifactCount: Int,
    val totalArtifactBytes: Long,
    val tables: List<String>,
    val artifacts: List<ConfigPublicationArtifactManifest>,
    val components: List<ConfigPublicationComponentManifest>,
)
