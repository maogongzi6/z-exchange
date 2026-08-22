package com.exchange.common.kafka.listener.metrics;

import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Records confirmed DLT consumption separately from listener failure classification.
 */
@Slf4j
public class KafkaListenerDltMetrics {
    private final MeterRegistry meterRegistry;
    private final String listener;

    public KafkaListenerDltMetrics(MeterRegistry meterRegistry, String listener) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.listener = MetricTagSanitizer.safeValue(listener);
    }

    public void record(String originalTopic) {
        try {
            /*
             * This counter is intentionally registered on demand. DLT events are rare, and
             * original_topic has bounded operational cardinality for a configured listener.
             */
            Counter.builder(CommonMetrics.KAFKA_LISTENER_DLT.name())
                    .description(CommonMetrics.KAFKA_LISTENER_DLT.description())
                    .tag(CommonMetricTags.LISTENER, listener)
                    .tag(CommonMetricTags.ORIGINAL_TOPIC, MetricTagSanitizer.safeValue(originalTopic))
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException e) {
            log.error(
                    "failed to record Kafka listener DLT metric, listener={}, originalTopic={}",
                    listener,
                    originalTopic,
                    e
            );
            // Observability failure must not make the DLT handler fail and redeliver the record.
        }
    }
}
