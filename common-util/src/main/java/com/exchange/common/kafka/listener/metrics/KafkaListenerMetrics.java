package com.exchange.common.kafka.listener.metrics;

import org.apache.kafka.common.header.Headers;

public interface KafkaListenerMetrics {
    void recordProcessingDelay(long recordTimestamp, Headers headers);

    void recordCompletion(KafkaListenerRequestOutcome outcome, long durationNanos);

    static KafkaListenerMetrics noop() {
        return NoopKafkaListenerMetrics.INSTANCE;
    }

    enum NoopKafkaListenerMetrics implements KafkaListenerMetrics {
        INSTANCE;

        @Override
        public void recordProcessingDelay(long recordTimestamp, Headers headers) {
        }

        @Override
        public void recordCompletion(KafkaListenerRequestOutcome outcome, long durationNanos) {
        }
    }
}
