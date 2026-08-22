package com.exchange.common.kafka.listener.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;

public enum KafkaListenerRequestOutcome {
    SUCCESS(CommonMetricTagValues.KafkaListenerOutcomes.SUCCESS, CommonMetricTagValues.Outcomes.SUCCESS),
    FAILURE_REPLIED(CommonMetricTagValues.KafkaListenerOutcomes.FAILURE_REPLIED, CommonMetricTagValues.Outcomes.ERROR),
    ACK_FAILURE(CommonMetricTagValues.KafkaListenerOutcomes.ACK_FAILURE, CommonMetricTagValues.Outcomes.ERROR),
    RETRYABLE(CommonMetricTagValues.KafkaListenerOutcomes.RETRYABLE, CommonMetricTagValues.Outcomes.ERROR),
    NON_RETRYABLE(CommonMetricTagValues.KafkaListenerOutcomes.NON_RETRYABLE, CommonMetricTagValues.Outcomes.ERROR);

    private final String metricValue;
    private final String durationOutcome;

    KafkaListenerRequestOutcome(String metricValue, String durationOutcome) {
        this.metricValue = metricValue;
        this.durationOutcome = durationOutcome;
    }

    public String metricValue() {
        return metricValue;
    }

    public String durationOutcome() {
        return durationOutcome;
    }
}
