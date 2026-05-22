package com.mikai233.common.runtime.module

import io.github.realmlabs.asteria.observability.Counter
import io.github.realmlabs.asteria.observability.MetricTags
import io.github.realmlabs.asteria.observability.Metrics
import io.github.realmlabs.asteria.observability.Timer
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ConcurrentHashMap
import io.prometheus.client.Counter as PrometheusCounter
import io.prometheus.client.Gauge as PrometheusGauge
import io.prometheus.client.Histogram as PrometheusHistogram

class PrometheusAsteriaMetrics(
    private val registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
) : Metrics {
    private val instruments = SharedInstruments.forRegistry(registry)

    override fun counter(name: String, tags: MetricTags): Counter {
        require(name.isNotBlank()) { "counter name must not be blank" }
        val labels = tags.labelNames()
        val counter = instruments.counters.computeIfAbsent(InstrumentKey(counterName(name), labels)) { key ->
            PrometheusCounter.build()
                .name(key.name)
                .help("Asteria counter $name")
                .labelNames(*key.labels.toTypedArray())
                .register(registry)
        }
        return PrometheusCounterAdapter(counter.labels(*tags.labelValues(labels)))
    }

    override fun timer(name: String, tags: MetricTags): Timer {
        require(name.isNotBlank()) { "timer name must not be blank" }
        val labels = tags.labelNames()
        val histogram = instruments.timers.computeIfAbsent(InstrumentKey(timerName(name), labels)) { key ->
            PrometheusHistogram.build()
                .name(key.name)
                .help("Asteria timer $name in milliseconds")
                .labelNames(*key.labels.toTypedArray())
                .buckets(*TimerBucketsMillis)
                .register(registry)
        }
        return PrometheusTimerAdapter(histogram.labels(*tags.labelValues(labels)))
    }

    override fun gauge(name: String, tags: MetricTags, value: () -> Double) {
        require(name.isNotBlank()) { "gauge name must not be blank" }
        val labels = tags.labelNames()
        val gauge = instruments.gauges.computeIfAbsent(InstrumentKey(metricName(name), labels)) { key ->
            PrometheusGauge.build()
                .name(key.name)
                .help("Asteria gauge $name")
                .labelNames(*key.labels.toTypedArray())
                .register(registry)
        }
        gauge.setChild<PrometheusGauge>(object : PrometheusGauge.Child() {
            override fun get(): Double = value()
        }, *tags.labelValues(labels))
    }

    private data class InstrumentKey(
        val name: String,
        val labels: List<String>,
    )

    private class SharedInstruments {
        val counters = ConcurrentHashMap<InstrumentKey, PrometheusCounter>()
        val timers = ConcurrentHashMap<InstrumentKey, PrometheusHistogram>()
        val gauges = ConcurrentHashMap<InstrumentKey, PrometheusGauge>()

        companion object {
            private val byRegistry = ConcurrentHashMap<CollectorRegistry, SharedInstruments>()

            fun forRegistry(registry: CollectorRegistry): SharedInstruments =
                byRegistry.computeIfAbsent(registry) { SharedInstruments() }
        }
    }

    private class PrometheusCounterAdapter(
        private val child: PrometheusCounter.Child,
    ) : Counter {
        override fun increment(amount: Long) {
            require(amount >= 0) { "counter increment amount must not be negative" }
            child.inc(amount.toDouble())
        }
    }

    private class PrometheusTimerAdapter(
        private val child: PrometheusHistogram.Child,
    ) : Timer {
        override suspend fun <T> record(block: suspend () -> T): T {
            val startedAt = System.nanoTime()
            return try {
                block()
            } finally {
                record((System.nanoTime() - startedAt) / NANOS_PER_MILLI)
            }
        }

        override fun record(durationMillis: Long) {
            require(durationMillis >= 0) { "timer duration must not be negative" }
            child.observe(durationMillis.toDouble())
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        val TimerBucketsMillis = doubleArrayOf(
            0.5,
            1.0,
            2.0,
            5.0,
            10.0,
            20.0,
            50.0,
            100.0,
            200.0,
            500.0,
            1_000.0,
            2_000.0,
            5_000.0,
            10_000.0,
        )

        fun counterName(name: String): String =
            metricName(name).removeSuffix("_total")

        fun timerName(name: String): String {
            val normalized = metricName(name)
            return if (normalized.endsWith("_duration")) {
                "${normalized}_milliseconds"
            } else {
                normalized
            }
        }

        fun metricName(name: String): String {
            val sanitized = name.sanitizePrometheusIdentifier()
            return if (sanitized.firstOrNull()?.isDigit() == true) {
                "_$sanitized"
            } else {
                sanitized
            }
        }

        fun MetricTags.labelNames(): List<String> =
            asMap().keys.map { it.sanitizePrometheusIdentifier() }.sorted()

        fun MetricTags.labelValues(labelNames: List<String>): Array<String> {
            val sanitizedValues = asMap().mapKeys { it.key.sanitizePrometheusIdentifier() }
            return labelNames.map { sanitizedValues.getValue(it) }.toTypedArray()
        }

        fun String.sanitizePrometheusIdentifier(): String {
            val sanitized = replace(Regex("[^A-Za-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            return sanitized.ifBlank { "unknown" }
        }
    }
}
