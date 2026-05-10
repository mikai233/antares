package com.mikai233.common.test

import com.mikai233.common.message.Message
import com.mikai233.common.test.msg.HandlerCtx
import com.mikai233.common.test.msg.TestMessageA
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.message.ActorHandlerContext
import io.github.realmlabs.asteria.message.MessageDispatcher
import io.github.realmlabs.asteria.message.MessageHandler
import io.github.realmlabs.asteria.message.PatchableMessageHandlerRegistry
import io.github.realmlabs.asteria.message.dispatchActor
import io.github.realmlabs.asteria.message.messageHandlers
import io.github.realmlabs.asteria.patch.InMemoryRuntimePatchRepository
import io.github.realmlabs.asteria.patch.PatchApplicationService
import io.github.realmlabs.asteria.patch.PatchArtifact
import io.github.realmlabs.asteria.patch.PatchCompatibility
import io.github.realmlabs.asteria.patch.PatchEnvironment
import io.github.realmlabs.asteria.patch.PatchId
import io.github.realmlabs.asteria.patch.PatchStatus
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import io.github.realmlabs.asteria.patch.RuntimePatchDescriptor
import io.github.realmlabs.asteria.patch.RuntimePatchInstallContext
import io.github.realmlabs.asteria.patch.RuntimePatchPlugin
import io.github.realmlabs.asteria.patch.RuntimePatchPluginResolver
import io.github.realmlabs.asteria.patch.RuntimePatchQuery
import io.github.realmlabs.asteria.patch.PatchRuntime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimePatchWorkflowTest {
    @Test
    fun `different patch ids stack and rollback by revision for services`() = runBlocking {
        val services = PatchableServiceRegistry().apply {
            register(TestService::class, TestService("base"))
        }
        val repository = InMemoryRuntimePatchRepository()
        val resolver = MutablePatchResolver()
        val runtime = TestRuntime(ServiceRegistry())
        val patchRuntime = PatchRuntime(runtime)
        val application = patchApplication(patchRuntime, repository, resolver)

        val first = repository.save(patchDescriptor("service-first"))
        val second = repository.save(patchDescriptor("service-second"))
        val disabled = repository.save(patchDescriptor("service-disabled", PatchStatus.Disabled))
        resolver.register(first.id, ServicePatch(services, "first"))
        resolver.register(second.id, ServicePatch(services, "second"))
        resolver.register(disabled.id, ServicePatch(services, "disabled"))

        val report = application.applyEnabledPatches()

        assertEquals(2, report.appliedCount)
        assertEquals("second", services.require(TestService::class).name)
        assertEquals(listOf(first.id, second.id), patchRuntime.appliedPatches().map { it.id })
        assertEquals(PatchStatus.Disabled, repository.find(disabled.id)?.status)

        application.disable(second.id)
        assertEquals("first", services.require(TestService::class).name)

        application.disable(first.id)
        assertEquals("base", services.require(TestService::class).name)
    }

    @Test
    fun `same patch id is ignored until repository revision changes`() = runBlocking {
        val services = PatchableServiceRegistry().apply {
            register(TestService::class, TestService("base"))
        }
        val repository = InMemoryRuntimePatchRepository()
        val resolver = MutablePatchResolver()
        val runtime = TestRuntime(ServiceRegistry())
        val patchRuntime = PatchRuntime(runtime)
        val application = patchApplication(patchRuntime, repository, resolver)

        val firstRevision = repository.save(patchDescriptor("same-service", artifactChecksum = "first"))
        resolver.register(firstRevision.id, ServicePatch(services, "first"))

        application.reconcileEnabledPatches()
        assertEquals("first", services.require(TestService::class).name)

        val ignored = application.apply(firstRevision.id)
        assertEquals("same-service", ignored.patchId.value)
        assertEquals("first", services.require(TestService::class).name)

        val secondRevision = repository.save(patchDescriptor("same-service", artifactChecksum = "second"))
        resolver.register(secondRevision.id, ServicePatch(services, "second"))

        val reconcileReport = application.reconcileEnabledPatches()

        assertEquals(listOf(firstRevision.id), reconcileReport.removedPatchIds)
        assertEquals(1, reconcileReport.appliedCount)
        assertEquals("second", services.require(TestService::class).name)
        assertEquals(listOf(secondRevision.execution()), patchRuntime.appliedPatches())
    }

    @Test
    fun `message handler patches follow the same stacking rules`() = runBlocking {
        val events = mutableListOf<String>()
        val registry = PatchableMessageHandlerRegistry<ActorHandlerContext<HandlerCtx>, Message>().apply {
            register(TestMessageA::class, MessageHandler { _, message -> events += "base:${message.name}" })
        }
        val dispatcher = MessageDispatcher(registry)
        val repository = InMemoryRuntimePatchRepository()
        val resolver = MutablePatchResolver()
        val runtime = TestRuntime(ServiceRegistry())
        val application = patchApplication(PatchRuntime(runtime), repository, resolver)

        val first = repository.save(patchDescriptor("handler-first"))
        val second = repository.save(patchDescriptor("handler-second"))
        resolver.register(first.id, MessageHandlerPatch(registry, events, "first"))
        resolver.register(second.id, MessageHandlerPatch(registry, events, "second"))

        application.applyEnabledPatches()
        dispatcher.dispatchActor(runtime, HandlerCtx, TestMessageA("hello"))
        application.disable(second.id)
        dispatcher.dispatchActor(runtime, HandlerCtx, TestMessageA("world"))
        application.disable(first.id)
        dispatcher.dispatchActor(runtime, HandlerCtx, TestMessageA("again"))

        assertEquals(listOf("second:hello", "first:world", "base:again"), events)
    }

    @Test
    fun `disabled patches are not applied during startup reconciliation`() = runBlocking {
        val services = PatchableServiceRegistry().apply {
            register(TestService::class, TestService("base"))
        }
        val repository = InMemoryRuntimePatchRepository()
        val resolver = MutablePatchResolver()
        val runtime = TestRuntime(ServiceRegistry())
        val patchRuntime = PatchRuntime(runtime)
        val application = patchApplication(patchRuntime, repository, resolver)

        val enabled = repository.save(patchDescriptor("enabled-service"))
        val disabled = repository.save(patchDescriptor("disabled-service", PatchStatus.Disabled))
        resolver.register(enabled.id, ServicePatch(services, "enabled"))
        resolver.register(disabled.id, ServicePatch(services, "disabled"))

        val report = application.reconcileEnabledPatches()

        assertEquals(1, report.appliedCount)
        assertEquals("enabled", services.require(TestService::class).name)
        assertEquals(listOf(enabled.id), patchRuntime.appliedPatches().map { it.id })
        assertFalse(repository.list(RuntimePatchQuery(status = PatchStatus.Disabled)).isEmpty())
    }

    private fun patchApplication(
        runtime: PatchRuntime,
        repository: InMemoryRuntimePatchRepository,
        resolver: MutablePatchResolver,
    ): PatchApplicationService {
        return PatchApplicationService(
            runtime = runtime,
            environment = PatchEnvironment(appName = "test", version = "1.0.0"),
            repository = repository,
            resolver = resolver,
        )
    }

    private fun patchDescriptor(
        id: String,
        status: PatchStatus = PatchStatus.Enabled,
        artifactChecksum: String = id,
    ): RuntimePatchDescriptor {
        return RuntimePatchDescriptor(
            id = PatchId(id),
            artifact = PatchArtifact(name = "$id.jar", checksum = artifactChecksum),
            compatibility = PatchCompatibility(appName = "test", versions = setOf("1.0.0")),
            status = status,
        )
    }

    private data class TestService(val name: String)

    private class ServicePatch(
        private val registry: PatchableServiceRegistry,
        private val name: String,
    ) : RuntimePatchPlugin {
        override suspend fun install(context: RuntimePatchInstallContext) {
            context.services.replace(registry, TestService::class, TestService(name))
        }
    }

    private class MessageHandlerPatch(
        private val registry: PatchableMessageHandlerRegistry<ActorHandlerContext<HandlerCtx>, Message>,
        private val events: MutableList<String>,
        private val name: String,
    ) : RuntimePatchPlugin {
        override suspend fun install(context: RuntimePatchInstallContext) {
            context.messageHandlers.replace(
                registry,
                TestMessageA::class,
                MessageHandler { _, message -> events += "$name:${message.name}" },
            )
        }
    }

    private class MutablePatchResolver : RuntimePatchPluginResolver {
        private val plugins = mutableMapOf<PatchId, RuntimePatchPlugin>()

        fun register(
            id: PatchId,
            plugin: RuntimePatchPlugin,
        ) {
            plugins[id] = plugin
        }

        override suspend fun resolve(patch: RuntimePatchDescriptor): RuntimePatchPlugin {
            return requireNotNull(plugins[patch.id]) { "patch plugin ${patch.id} not found" }
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
