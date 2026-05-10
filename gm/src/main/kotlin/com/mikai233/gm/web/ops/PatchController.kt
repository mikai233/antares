package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.runtime.versionText
import com.mikai233.gm.GmNode
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.patch.PatchArtifact
import io.github.realmlabs.asteria.patch.PatchClusterApplicationService
import io.github.realmlabs.asteria.patch.PatchCompatibility
import io.github.realmlabs.asteria.patch.PatchId
import io.github.realmlabs.asteria.patch.PatchRequirements
import io.github.realmlabs.asteria.patch.PatchStatus
import io.github.realmlabs.asteria.patch.PatchTarget
import io.github.realmlabs.asteria.patch.RuntimePatchDescriptor
import io.github.realmlabs.asteria.patch.RuntimePatchNodeResult
import io.github.realmlabs.asteria.patch.RuntimePatchRepository
import io.github.realmlabs.asteria.patch.RuntimePatchQuery
import io.github.realmlabs.asteria.patch.WritablePatchArtifactStore
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@AllOpen
@RestController
@RequestMapping("/gm/api/patches")
class PatchController(
    private val node: GmNode,
) {
    private val repository: RuntimePatchRepository
        get() = node.services.get(RuntimePatchRepository::class)

    private val artifacts: WritablePatchArtifactStore
        get() = node.services.get(WritablePatchArtifactStore::class)

    private val cluster: PatchClusterApplicationService
        get() = node.services.get(PatchClusterApplicationService::class)

    @GetMapping
    suspend fun list(
        @RequestParam(required = false) status: PatchStatus?,
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) version: String?,
    ): List<PatchDescriptorResponse> {
        return repository.list(RuntimePatchQuery(status = status, appName = appName, version = version))
            .map(RuntimePatchDescriptor::toResponse)
    }

    @PostMapping("/publish", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun publish(
        @RequestPart("request") request: PatchPublishRequest,
        @RequestPart("file") file: MultipartFile,
    ): PatchDescriptorResponse {
        return publishDescriptor(request.toCommand(file)).toResponse()
    }

    @PostMapping("/publish-and-apply", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun publishAndApply(
        @RequestPart("request") request: PatchPublishRequest,
        @RequestPart("file") file: MultipartFile,
    ): PatchClusterApplyResponse {
        val descriptor = publishDescriptor(request.toCommand(file, status = PatchStatus.Enabled))
        return cluster.apply(descriptor.id).toResponse()
    }

    @PostMapping("/{id}/apply")
    suspend fun apply(@PathVariable id: String): PatchClusterApplyResponse {
        return cluster.apply(PatchId(id)).toResponse()
    }

    @PostMapping("/{id}/disable")
    suspend fun disable(@PathVariable id: String): PatchClusterApplyResponse {
        return cluster.disable(PatchId(id)).toResponse()
    }

    private suspend fun publishDescriptor(command: PatchPublishCommand): RuntimePatchDescriptor {
        require(!command.file.isEmpty) { "patch file must not be empty" }
        val artifact = artifacts.save(
            name = command.file.originalFilename?.takeIf(String::isNotBlank) ?: "${command.id}.jar",
            bytes = command.file.bytes,
            version = versionText(),
        )
        return repository.save(
            RuntimePatchDescriptor(
                id = PatchId(command.id),
                name = command.name?.takeIf(String::isNotBlank) ?: command.id,
                artifact = artifact,
                compatibility = PatchCompatibility(
                    appName = command.appName?.takeIf(String::isNotBlank) ?: node.name,
                    versions = command.versions.toVersionSet(),
                ),
                requirements = PatchRequirements(
                    roles = command.requiredRoles.toRoleSet(),
                    modules = command.requiredModules.normalizedStringSet(),
                    capabilities = command.requiredCapabilities.normalizedStringSet(),
                ),
                target = command.roles.toTarget(),
                status = command.status,
            ),
        )
    }

    private fun Set<String>?.toVersionSet(): Set<String> {
        return this
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?.takeIf(Set<String>::isNotEmpty)
            ?: setOf(versionText())
    }

    private fun Set<String>?.toTarget(): PatchTarget {
        val parsedRoles = toRoleSet()
        return if (parsedRoles.isEmpty()) PatchTarget.AllNodes else PatchTarget.Roles(parsedRoles)
    }

    private fun Set<String>?.toRoleSet(): Set<RoleKey> {
        return normalizedStringSet().mapTo(linkedSetOf(), ::RoleKey)
    }

    private fun Set<String>?.normalizedStringSet(): Set<String> {
        return this
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()
    }
}

data class PatchPublishRequest(
    val id: String,
    val name: String? = null,
    val appName: String? = null,
    val versions: Set<String>? = null,
    val roles: Set<String>? = null,
    val requiredRoles: Set<String>? = null,
    val requiredModules: Set<String>? = null,
    val requiredCapabilities: Set<String>? = null,
    val status: PatchStatus = PatchStatus.Enabled,
)

private fun PatchPublishRequest.toCommand(
    file: MultipartFile,
    status: PatchStatus = this.status,
): PatchPublishCommand {
    return PatchPublishCommand(
        id = id,
        file = file,
        name = name,
        appName = appName,
        versions = versions,
        roles = roles,
        requiredRoles = requiredRoles,
        requiredModules = requiredModules,
        requiredCapabilities = requiredCapabilities,
        status = status,
    )
}

private data class PatchPublishCommand(
    val id: String,
    val file: MultipartFile,
    val name: String?,
    val appName: String?,
    val versions: Set<String>?,
    val roles: Set<String>?,
    val requiredRoles: Set<String>?,
    val requiredModules: Set<String>?,
    val requiredCapabilities: Set<String>?,
    val status: PatchStatus,
)

data class PatchDescriptorResponse(
    val id: String,
    val name: String,
    val artifact: PatchArtifactResponse,
    val appName: String,
    val versions: Set<String>,
    val target: String,
    val requirements: PatchRequirementsResponse,
    val status: String,
    val revision: Long,
)

data class PatchArtifactResponse(
    val name: String,
    val checksum: String,
    val version: String?,
)

data class PatchRequirementsResponse(
    val roles: Set<String>,
    val modules: Set<String>,
    val capabilities: Set<String>,
)

data class PatchClusterApplyResponse(
    val patchId: String,
    val succeeded: Boolean,
    val results: List<PatchNodeResultResponse>,
)

data class PatchNodeResultResponse(
    val patchId: String,
    val nodeId: String?,
    val address: String,
    val appName: String,
    val version: String,
    val roles: Set<String>,
    val status: String,
    val attempt: Int,
    val operationCount: Int?,
    val message: String?,
)

private fun RuntimePatchDescriptor.toResponse(): PatchDescriptorResponse {
    return PatchDescriptorResponse(
        id = id.value,
        name = name,
        artifact = artifact.toResponse(),
        appName = compatibility.appName,
        versions = compatibility.versions,
        target = target.description(),
        requirements = requirements.toResponse(),
        status = status.name,
        revision = revision,
    )
}

private fun PatchArtifact.toResponse(): PatchArtifactResponse {
    return PatchArtifactResponse(name = name, checksum = checksum, version = version)
}

private fun PatchRequirements.toResponse(): PatchRequirementsResponse {
    return PatchRequirementsResponse(
        roles = roles.mapTo(linkedSetOf()) { it.value },
        modules = modules,
        capabilities = capabilities,
    )
}

private fun PatchTarget.description(): String {
    return when (this) {
        PatchTarget.AllNodes -> "all"
        is PatchTarget.Nodes -> "nodes:${addresses.sorted().joinToString(",")}"
        is PatchTarget.Roles -> "roles:${roles.map { it.value }.sorted().joinToString(",")}"
    }
}

private fun io.github.realmlabs.asteria.patch.PatchClusterApplyResult.toResponse(): PatchClusterApplyResponse {
    return PatchClusterApplyResponse(
        patchId = patchId.value,
        succeeded = succeeded,
        results = results.map(RuntimePatchNodeResult::toResponse),
    )
}

private fun RuntimePatchNodeResult.toResponse(): PatchNodeResultResponse {
    return PatchNodeResultResponse(
        patchId = patchId.value,
        nodeId = nodeId,
        address = address,
        appName = appName,
        version = version,
        roles = roles.mapTo(linkedSetOf()) { it.value },
        status = status.name,
        attempt = attempt,
        operationCount = operationCount,
        message = message,
    )
}
