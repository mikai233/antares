package com.mikai233.common.runtime

import com.google.protobuf.Message as ProtobufMessage
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.observability.MetricTags
import io.github.realmlabs.asteria.observability.Metrics
import io.github.realmlabs.asteria.observability.NoopMetrics

fun NodeRuntime.recordMessageDispatch(
    actor: String,
    dispatcher: String,
    message: Any,
    block: () -> Unit,
) {
    val metrics = services.find(Metrics::class) ?: NoopMetrics
    val tags = MetricTags.of(
        "role" to roles.map { it.value }.sorted().joinToString(",").ifBlank { "unknown" },
        "actor" to actor,
        "dispatcher" to dispatcher,
        "message" to message.metricMessageName(),
    )
    metrics.counter("antares.message.dispatch.total", tags).increment()
    val startedAt = System.nanoTime()
    try {
        block()
        metrics.counter("antares.message.dispatch.succeeded.total", tags).increment()
    } catch (error: Throwable) {
        metrics.counter("antares.message.dispatch.failed.total", tags).increment()
        throw error
    } finally {
        metrics.timer("antares.message.dispatch.duration", tags)
            .record((System.nanoTime() - startedAt) / NANOS_PER_MILLI)
    }
}

fun Any.metricMessageName(): String {
    return when (this) {
        is ProtobufMessage -> descriptorForType.fullName
        else -> this::class.qualifiedName ?: this::class.simpleName ?: javaClass.name
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
