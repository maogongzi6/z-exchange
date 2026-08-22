package com.exchange.common.kafka.listener.metrics;

import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaListenerDltMetricsTest {

    @Test
    void recordsConfirmedDltConsumptionByListenerAndOriginalTopic() {
        // DLT is counted here as a distinct routing event, not as an onMessage outcome.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaListenerDltMetrics metrics = new KafkaListenerDltMetrics(registry, "wallet_post");

        metrics.record("wallet.ledger.post");

        assertEquals(
                1.0,
                registry.find(CommonMetrics.KAFKA_LISTENER_DLT.name())
                        .tag(CommonMetricTags.LISTENER, "wallet_post")
                        .tag(CommonMetricTags.ORIGINAL_TOPIC, "wallet.ledger.post")
                        .counter()
                        .count()
        );
    }
}
