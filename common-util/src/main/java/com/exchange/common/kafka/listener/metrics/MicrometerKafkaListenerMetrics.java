package com.exchange.common.kafka.listener.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-cardinality listener meters for one configured listener endpoint.
 */
@Slf4j
public class MicrometerKafkaListenerMetrics implements KafkaListenerMetrics {
    private final Clock clock;
    private final Map<KafkaListenerRequestOutcome, Counter> requestCounters =
            new EnumMap<>(KafkaListenerRequestOutcome.class);
    private final Timer successDuration;
    private final Timer failureDuration;
    private final Timer immediateProcessingDelay;
    private final Timer retryProcessingDelay;

    public MicrometerKafkaListenerMetrics(MeterRegistry meterRegistry, String listener) {
        this(meterRegistry, listener, Clock.systemUTC());
    }

    MicrometerKafkaListenerMetrics(MeterRegistry meterRegistry, String listener, Clock clock) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        String safeListener = MetricTagSanitizer.safeValue(listener);

        for (KafkaListenerRequestOutcome outcome : KafkaListenerRequestOutcome.values()) {
            requestCounters.put(outcome, registerCounterSafely(meterRegistry, safeListener, outcome));
        }
        successDuration = registerDurationSafely(
                meterRegistry,
                safeListener,
                CommonMetricTagValues.Outcomes.SUCCESS
        );
        failureDuration = registerDurationSafely(
                meterRegistry,
                safeListener,
                CommonMetricTagValues.Outcomes.ERROR
        );
        immediateProcessingDelay = registerProcessingDelaySafely(
                meterRegistry,
                safeListener,
                CommonMetricTagValues.KafkaSources.IMMEDIATE
        );
        retryProcessingDelay = registerProcessingDelaySafely(
                meterRegistry,
                safeListener,
                CommonMetricTagValues.KafkaSources.RETRY
        );
    }

    @Override
    public void recordProcessingDelay(long recordTimestamp, Headers headers) {
        try {
            Header originalTimestampHeader = headers == null
                    ? null
                    : headers.lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ORIGINAL_TIMESTAMP);
            boolean retry = originalTimestampHeader != null;
            long originalTimestamp = retry
                    ? timestampFromHeader(originalTimestampHeader, recordTimestamp)
                    : recordTimestamp;
            if (originalTimestamp < 0) {
                return;
            }

            /*
             * This is a cross-host wall-clock measurement: the record timestamp comes from the
             * producer with CreateTime or the broker with LogAppendTime, while clock.millis() is
             * read on the consumer. Those hosts must be NTP-synchronized because clock skew
             * directly biases the delay. Clamping protects the Timer from negative values but
             * cannot correct that skew.
             */
            long delayMillis = Math.max(0L, clock.millis() - originalTimestamp);
            Timer timer = retry ? retryProcessingDelay : immediateProcessingDelay;
            if (timer != null) {
                timer.record(delayMillis, TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            log.error("failed to record Kafka listener processing delay metric", e);
            // Listener metrics must not affect record processing.
        }
    }

    @Override
    public void recordCompletion(KafkaListenerRequestOutcome outcome, long durationNanos) {
        try {
            KafkaListenerRequestOutcome safeOutcome = outcome == null
                    ? KafkaListenerRequestOutcome.NON_RETRYABLE
                    : outcome;
            Counter counter = requestCounters.get(safeOutcome);
            if (counter != null) {
                counter.increment();
            }
            Timer timer = CommonMetricTagValues.Outcomes.SUCCESS.equals(safeOutcome.durationOutcome())
                    ? successDuration
                    : failureDuration;
            if (timer != null) {
                timer.record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
            }
        } catch (RuntimeException e) {
            log.error("failed to record Kafka listener completion metric, outcome={}", outcome, e);
            // Listener metrics must not change acknowledgment or retry behavior.
        }
    }

    private Counter registerCounterSafely(
            MeterRegistry registry,
            String listener,
            KafkaListenerRequestOutcome outcome
    ) {
        try {
            return Counter.builder(CommonMetrics.KAFKA_LISTENER_REQUESTS.name())
                    .description(CommonMetrics.KAFKA_LISTENER_REQUESTS.description())
                    .tag(CommonMetricTags.LISTENER, listener)
                    .tag(CommonMetricTags.OUTCOME, outcome.metricValue())
                    .register(registry);
        } catch (RuntimeException e) {
            log.error(
                    "failed to register Kafka listener request metric, listener={}, outcome={}",
                    listener,
                    outcome.metricValue(),
                    e
            );
            return null;
        }
    }

    private Timer registerDurationSafely(MeterRegistry registry, String listener, String outcome) {
        try {
            return Timer.builder(CommonMetrics.KAFKA_LISTENER_DURATION.name())
                    .description(CommonMetrics.KAFKA_LISTENER_DURATION.description())
                    .tag(CommonMetricTags.LISTENER, listener)
                    .tag(CommonMetricTags.OUTCOME, outcome)
                    .register(registry);
        } catch (RuntimeException e) {
            log.error(
                    "failed to register Kafka listener duration metric, listener={}, outcome={}",
                    listener,
                    outcome,
                    e
            );
            return null;
        }
    }

    private Timer registerProcessingDelaySafely(MeterRegistry registry, String listener, String source) {
        try {
            return Timer.builder(CommonMetrics.KAFKA_LISTENER_PROCESSING_DELAY.name())
                    .description(CommonMetrics.KAFKA_LISTENER_PROCESSING_DELAY.description())
                    .tag(CommonMetricTags.LISTENER, listener)
                    .tag(CommonMetricTags.SOURCE, source)
                    .register(registry);
        } catch (RuntimeException e) {
            log.error(
                    "failed to register Kafka listener processing delay metric, listener={}, source={}",
                    listener,
                    source,
                    e
            );
            return null;
        }
    }

    private long timestampFromHeader(Header header, long fallback) {
        byte[] value = header.value();
        if (value == null || value.length == 0) {
            return fallback;
        }
        if (value.length == Long.BYTES) {
            return ByteBuffer.wrap(value).getLong();
        }
        try {
            return Long.parseLong(new String(value, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            try {
                return new BigInteger(value).longValueExact();
            } catch (ArithmeticException ignoredAgain) {
                return fallback;
            }
        }
    }
}
