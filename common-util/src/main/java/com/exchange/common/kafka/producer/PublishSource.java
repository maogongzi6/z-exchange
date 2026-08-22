package com.exchange.common.kafka.producer;

import com.exchange.common.metrics.CommonMetricTagValues;

public enum PublishSource {
    IMMEDIATE(CommonMetricTagValues.KafkaSources.IMMEDIATE),
    RETRY(CommonMetricTagValues.KafkaSources.RETRY);

    private final String metricValue;

    PublishSource(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
