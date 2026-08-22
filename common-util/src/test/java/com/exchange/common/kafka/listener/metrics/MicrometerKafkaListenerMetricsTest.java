package com.exchange.common.kafka.listener.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerKafkaListenerMetricsTest {
    private static final long NOW_MILLIS = Instant.parse("2026-08-22T08:00:00Z").toEpochMilli();

    private SimpleMeterRegistry registry;
    private MicrometerKafkaListenerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerKafkaListenerMetrics(
                registry,
                "wallet_post",
                Clock.fixed(Instant.ofEpochMilli(NOW_MILLIS), ZoneOffset.UTC)
        );
    }

    @Test
    void recordsImmediateAndRetryDelayFromTheirCorrectTimestamps() {
        // Initial delivery uses the record timestamp; retry delivery preserves the original header.
        metrics.recordProcessingDelay(NOW_MILLIS - 100, new RecordHeaders());

        RecordHeaders retryHeaders = new RecordHeaders();
        retryHeaders.add(
                RetryTopicHeaders.DEFAULT_HEADER_ORIGINAL_TIMESTAMP,
                BigInteger.valueOf(NOW_MILLIS - 2_000).toByteArray()
        );
        metrics.recordProcessingDelay(NOW_MILLIS - 50, retryHeaders);

        assertEquals(0.1, processingDelay(CommonMetricTagValues.KafkaSources.IMMEDIATE));
        assertEquals(2.0, processingDelay(CommonMetricTagValues.KafkaSources.RETRY));
    }

    @Test
    void recordsOneRequestOutcomeAndLowCardinalityDurationOutcome() {
        metrics.recordCompletion(KafkaListenerRequestOutcome.SUCCESS, TimeUnit.MILLISECONDS.toNanos(12));
        metrics.recordCompletion(KafkaListenerRequestOutcome.FAILURE_REPLIED, TimeUnit.MILLISECONDS.toNanos(8));

        assertEquals(1, requestCount(CommonMetricTagValues.KafkaListenerOutcomes.SUCCESS));
        assertEquals(1, requestCount(CommonMetricTagValues.KafkaListenerOutcomes.FAILURE_REPLIED));
        assertEquals(1, durationCount(CommonMetricTagValues.Outcomes.SUCCESS));
        assertEquals(1, durationCount(CommonMetricTagValues.Outcomes.ERROR));
    }

    private double processingDelay(String source) {
        return registry.find(CommonMetrics.KAFKA_LISTENER_PROCESSING_DELAY.name())
                .tag(CommonMetricTags.LISTENER, "wallet_post")
                .tag(CommonMetricTags.SOURCE, source)
                .timer()
                .totalTime(TimeUnit.SECONDS);
    }

    private double requestCount(String outcome) {
        return registry.find(CommonMetrics.KAFKA_LISTENER_REQUESTS.name())
                .tag(CommonMetricTags.LISTENER, "wallet_post")
                .tag(CommonMetricTags.OUTCOME, outcome)
                .counter()
                .count();
    }

    private long durationCount(String outcome) {
        return registry.find(CommonMetrics.KAFKA_LISTENER_DURATION.name())
                .tag(CommonMetricTags.LISTENER, "wallet_post")
                .tag(CommonMetricTags.OUTCOME, outcome)
                .timer()
                .count();
    }
}
