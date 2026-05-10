package com.mikai233.common.test

import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import com.mikai233.common.config.PATCH_ARTIFACTS
import com.mikai233.common.config.PATCH_DESCRIPTORS
import com.mikai233.common.config.PATCH_REVISION
import com.mikai233.common.runtime.patch.ConfigCenterPatchArtifactStore
import com.mikai233.common.runtime.patch.ConfigCenterRuntimePatchRepository
import io.github.realmlabs.asteria.config.center.InMemoryConfigStore
import io.github.realmlabs.asteria.patch.InMemoryRuntimePatchNodeResultRepository
import io.github.realmlabs.asteria.patch.LocalPatchNodeClient
import io.github.realmlabs.asteria.patch.LocalPatchNodeProvider
import io.github.realmlabs.asteria.patch.PatchApplicationService
import io.github.realmlabs.asteria.patch.PatchArtifact
import io.github.realmlabs.asteria.patch.PatchClusterApplicationService
import io.github.realmlabs.asteria.patch.PatchCompatibility
import io.github.realmlabs.asteria.patch.PatchEnvironment
import io.github.realmlabs.asteria.patch.PatchId
import io.github.realmlabs.asteria.patch.PatchStatus
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import io.github.realmlabs.asteria.patch.RuntimePatch
import io.github.realmlabs.asteria.patch.RuntimePatchDescriptor
import io.github.realmlabs.asteria.patch.RuntimePatchInstallContext
import io.github.realmlabs.asteria.patch.RuntimePatchNodeStatus
import io.github.realmlabs.asteria.patch.RuntimePatchPlugin
import io.github.realmlabs.asteria.patch.PatchRuntime
import io.github.realmlabs.asteria.patch.jar.JarRuntimePatchPluginResolver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class RuntimePatchEndToEndTest {
    @Test
    fun `gm publication stores jar artifact and applies patch through cluster service`() = runBlocking {
        val fixture = PatchFlowFixture()
        val publisher = fixture.publisher()

        val applyResult = publisher.publishAndApply(
            id = "gm-service-patch",
            artifactName = "gm-service-patch.jar",
            pluginClassName = PublishedServicePatchPlugin::class.java.name,
        )

        assertEquals(RuntimePatchNodeStatus.Applied, applyResult.results.single().status)
        assertEquals("published", fixture.services.require(PublishedService::class).name)
        assertEquals(listOf(PatchId("gm-service-patch")), fixture.runtime.appliedPatches().map(RuntimePatch::id))

        val disableResult = fixture.cluster.disable(PatchId("gm-service-patch"))

        assertEquals(RuntimePatchNodeStatus.Removed, disableResult.results.single().status)
        assertEquals("base", fixture.services.require(PublishedService::class).name)
    }

    @Test
    fun `gm enabled batch skips disabled published patches`() = runBlocking {
        val fixture = PatchFlowFixture()
        val publisher = fixture.publisher()

        publisher.publish(
            id = "disabled-patch",
            artifactName = "disabled-patch.jar",
            pluginClassName = PublishedServicePatchPlugin::class.java.name,
            status = PatchStatus.Disabled,
        )

        val results = fixture.cluster.applyEnabledPatches()

        assertTrue(results.isEmpty())
        assertEquals("base", fixture.services.require(PublishedService::class).name)
        assertEquals(PatchStatus.Disabled, fixture.repository.find(PatchId("disabled-patch"))?.status)
    }

    @Test
    fun `same id publication is not refreshed by direct gm apply until node reconciles`() = runBlocking {
        val fixture = PatchFlowFixture()
        val publisher = fixture.publisher()

        publisher.publishAndApply(
            id = "same-id-patch",
            artifactName = "same-id-first.jar",
            pluginClassName = FirstPublishedServicePatchPlugin::class.java.name,
        )
        assertEquals("first", fixture.services.require(PublishedService::class).name)

        publisher.publishAndApply(
            id = "same-id-patch",
            artifactName = "same-id-second.jar",
            pluginClassName = SecondPublishedServicePatchPlugin::class.java.name,
        )

        assertEquals("first", fixture.services.require(PublishedService::class).name)

        val reconcileReport = fixture.nodeApplication.reconcileEnabledPatches()

        assertEquals(listOf(PatchId("same-id-patch")), reconcileReport.removedPatchIds)
        assertEquals(1, reconcileReport.appliedCount)
        assertEquals("second", fixture.services.require(PublishedService::class).name)
    }

    private class PatchFlowFixture {
        val services = PatchableServiceRegistry().apply {
            register(PublishedService::class, PublishedService("base"))
        }
        val runtimeServices = ServiceRegistry().apply {
            register(PublishedPatchBindings::class, PublishedPatchBindings(services))
        }
        val runtime = PatchRuntime(TestRuntime(runtimeServices))
        val store = InMemoryConfigStore()
        val repository = ConfigCenterRuntimePatchRepository(store, PATCH_DESCRIPTORS, PATCH_REVISION)
        val artifacts = ConfigCenterPatchArtifactStore(store, PATCH_ARTIFACTS)
        val environment = PatchEnvironment(appName = "test", version = "1.0.0")
        val nodeApplication = PatchApplicationService(
            runtime = runtime,
            environment = environment,
            repository = repository,
            resolver = JarRuntimePatchPluginResolver(artifacts),
        )
        val cluster = PatchClusterApplicationService(
            repository = repository,
            nodes = LocalPatchNodeProvider(environment),
            client = LocalPatchNodeClient(nodeApplication),
            results = InMemoryRuntimePatchNodeResultRepository(),
        )

        fun publisher(): TestGmPatchPublisher {
            return TestGmPatchPublisher(artifacts, repository, cluster, environment)
        }
    }

    private class TestGmPatchPublisher(
        private val artifacts: ConfigCenterPatchArtifactStore,
        private val repository: ConfigCenterRuntimePatchRepository,
        private val cluster: PatchClusterApplicationService,
        private val environment: PatchEnvironment,
    ) {
        suspend fun publishAndApply(
            id: String,
            artifactName: String,
            pluginClassName: String,
            status: PatchStatus = PatchStatus.Enabled,
        ) = cluster.apply(publish(id, artifactName, pluginClassName, status).id)

        suspend fun publish(
            id: String,
            artifactName: String,
            pluginClassName: String,
            status: PatchStatus = PatchStatus.Enabled,
        ): RuntimePatchDescriptor {
            val artifact = saveArtifact(artifactName, pluginClassName)
            return repository.save(
                RuntimePatchDescriptor(
                    id = PatchId(id),
                    artifact = artifact,
                    compatibility = PatchCompatibility(
                        appName = environment.appName,
                        versions = setOf(environment.version),
                    ),
                    status = status,
                ),
            )
        }

        private suspend fun saveArtifact(
            artifactName: String,
            pluginClassName: String,
        ): PatchArtifact {
            return artifacts.save(
                name = artifactName,
                bytes = patchJarBytes(pluginClassName),
                version = environment.version,
            )
        }

        private fun patchJarBytes(pluginClassName: String): ByteArray {
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue(JarRuntimePatchPluginResolver.PATCH_CLASS_NAME, pluginClassName)
            }
            return ByteArrayOutputStream().use { output ->
                JarOutputStream(output, manifest).use {
                    // The test plugin class is already on the parent classloader. The JAR still exercises artifact
                    // persistence, checksum validation, manifest parsing, resolver caching, and plugin instantiation.
                }
                output.toByteArray()
            }
        }
    }

    private class TestRuntime(
        override val services: ServiceRegistry,
    ) : NodeRuntime {
        override val name: String = "test"
        override val roles: Set<RoleKey> = emptySet()
        override val state: NodeState = NodeState.Started
    }
}

data class PublishedService(val name: String)

data class PublishedPatchBindings(val services: PatchableServiceRegistry)

class PublishedServicePatchPlugin : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get(PublishedPatchBindings::class)
        context.services.replace(bindings.services, PublishedService::class, PublishedService("published"))
    }
}

class FirstPublishedServicePatchPlugin : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get(PublishedPatchBindings::class)
        context.services.replace(bindings.services, PublishedService::class, PublishedService("first"))
    }
}

class SecondPublishedServicePatchPlugin : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get(PublishedPatchBindings::class)
        context.services.replace(bindings.services, PublishedService::class, PublishedService("second"))
    }
}
