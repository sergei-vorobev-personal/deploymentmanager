package com.kineto.deploymentmanager.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import software.amazon.awssdk.metrics.MetricCollection
import software.amazon.awssdk.metrics.MetricPublisher
import software.amazon.awssdk.metrics.SdkMetric
import java.time.Duration

class MicrometerAWSMetricsPublisher(
    private val awsServiceName: String,
    private val meterRegistry: MeterRegistry
) : MetricPublisher {

    override fun publish(metricCollection: MetricCollection) {
        recordCollection(metricCollection)
    }

    private fun recordCollection(collection: MetricCollection) {
        collection.forEach { recordMetric(it.metric(), it.value()) }
        collection.children().forEach { recordCollection(it) }
    }

    private fun recordMetric(metric: SdkMetric<*>, value: Any?) {
        val metricName = "aws.sdk.${awsServiceName}.${metric.name()}"

        when (value) {
            is Duration -> {
                Timer.builder(metricName)
                    .register(meterRegistry)
                    .record(value)
            }

            is Number -> {
                meterRegistry.gauge(metricName, value.toDouble())
            }
        }
    }

    override fun close() {}
}
