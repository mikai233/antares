package com.mikai233.common.runtime.module

import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import io.github.realmlabs.asteria.observability.Metrics
import io.github.realmlabs.asteria.observability.Observability
import io.github.realmlabs.asteria.observability.Tracer
import io.github.realmlabs.asteria.observability.NoopTracer
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class PrometheusMetricsModule(
    private val port: Int,
) : AsteriaModule {
    override val name: String = "prometheus-metrics"

    private val logger = LoggerFactory.getLogger(PrometheusMetricsModule::class.java)
    private var server: HTTPServer? = null
    private val metrics = PrometheusAsteriaMetrics()

    override suspend fun install(context: ModuleContext) {
        val tracer = context.services.find(Tracer::class) ?: NoopTracer
        context.services.register(Metrics::class, metrics)
        context.services.register(Observability::class, Observability(tracer = tracer, metrics = metrics))
    }

    override suspend fun start(context: ModuleContext) {
        if (defaultExportsInitialized.compareAndSet(false, true)) {
            DefaultExports.initialize()
        }
        server = HTTPServer(port)
        logger.info("Prometheus metrics available at http://localhost:{}/metrics", port)
    }

    override suspend fun stop(context: ModuleContext) {
        server?.close()
        server = null
    }

    private companion object {
        val defaultExportsInitialized = AtomicBoolean(false)
    }
}
