package com.mikai233.common.runtime.patch

import io.github.realmlabs.asteria.config.center.ConfigPath
import io.github.realmlabs.asteria.config.center.ConfigRevisionMismatchException
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.patch.PatchArtifact
import io.github.realmlabs.asteria.patch.PatchId
import io.github.realmlabs.asteria.patch.PatchStatus
import io.github.realmlabs.asteria.patch.RuntimePatchDescriptor
import io.github.realmlabs.asteria.patch.RuntimePatchQuery
import io.github.realmlabs.asteria.patch.RuntimePatchRepository
import io.github.realmlabs.asteria.patch.WritablePatchArtifactStore
import io.github.realmlabs.asteria.patch.patchArtifactSha256Checksum
import io.github.realmlabs.asteria.patch.verifyPatchArtifactChecksum
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64

class ConfigCenterRuntimePatchRepository(
    private val store: ConfigStore,
    private val descriptorRoot: ConfigPath,
    private val revisionPath: ConfigPath,
) : RuntimePatchRepository {
    private val localLock = Mutex()

    override suspend fun nextRevision(): Long {
        while (true) {
            val current = store.get(revisionPath)
            val currentValue = current?.bytes?.decodeObject<Long>() ?: 0L
            val nextValue = currentValue + 1
            try {
                store.put(revisionPath, nextValue.encodeObject(), current?.revision)
                return nextValue
            } catch (_: ConfigRevisionMismatchException) {
                continue
            }
        }
    }

    override suspend fun save(patch: RuntimePatchDescriptor): RuntimePatchDescriptor {
        return localLock.withLock {
            val existing = find(patch.id)
            val stored = when {
                patch.revision <= 0 -> patch.copy(revision = nextRevision())
                existing != null && patch != existing -> patch.copy(revision = nextRevision())
                else -> patch
            }
            store.put(descriptorPath(stored.id), stored.encodeObject())
            stored
        }
    }

    override suspend fun find(id: PatchId): RuntimePatchDescriptor? {
        return store.get(descriptorPath(id))?.bytes?.decodeObject()
    }

    override suspend fun list(query: RuntimePatchQuery): List<RuntimePatchDescriptor> {
        return store.children(descriptorRoot)
            .asSequence()
            .map { entry -> entry.bytes.decodeObject<RuntimePatchDescriptor>() }
            .filter { patch -> query.status == null || patch.status == query.status }
            .filter { patch -> query.appName == null || patch.compatibility.appName == query.appName }
            .filter { patch -> query.version == null || query.version in patch.compatibility.versions }
            .sortedBy { it.revision }
            .toList()
    }

    override suspend fun updateStatus(
        id: PatchId,
        status: PatchStatus,
    ): RuntimePatchDescriptor? {
        return localLock.withLock {
            val patch = find(id) ?: return@withLock null
            patch.copy(status = status).also { store.put(descriptorPath(id), it.encodeObject()) }
        }
    }

    private fun descriptorPath(id: PatchId): ConfigPath {
        return descriptorRoot / Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(id.value.toByteArray(Charsets.UTF_8))
    }
}

class ConfigCenterPatchArtifactStore(
    private val store: ConfigStore,
    private val artifactRoot: ConfigPath,
) : WritablePatchArtifactStore {
    override suspend fun save(
        name: String,
        bytes: ByteArray,
        version: String?,
    ): PatchArtifact {
        val artifact = PatchArtifact(name = name, checksum = patchArtifactSha256Checksum(bytes), version = version)
        store.put(artifactPath(artifact), bytes)
        return artifact
    }

    override suspend fun load(artifact: PatchArtifact): ByteArray {
        val bytes = requireNotNull(store.get(artifactPath(artifact))?.bytes) {
            "patch artifact ${artifact.name} with checksum ${artifact.checksum} not found"
        }
        verifyPatchArtifactChecksum(artifact, bytes)
        return bytes
    }

    private fun artifactPath(artifact: PatchArtifact): ConfigPath {
        val checksum = artifact.checksum.removePrefix("sha256:").lowercase()
        require(checksum.length == SHA_256_HEX_LENGTH && checksum.all { it in '0'..'9' || it in 'a'..'f' }) {
            "patch artifact checksum must be a sha256 hex value"
        }
        return artifactRoot / checksum
    }
}

private fun Any.encodeObject(): ByteArray {
    return ByteArrayOutputStream().use { bytes ->
        ObjectOutputStream(bytes).use { output -> output.writeObject(this) }
        bytes.toByteArray()
    }
}

private inline fun <reified T : Any> ByteArray.decodeObject(): T {
    return ObjectInputStream(ByteArrayInputStream(this)).use { input ->
        input.readObject() as T
    }
}

private const val SHA_256_HEX_LENGTH: Int = 64
