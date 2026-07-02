package com.exchange.common.metrics;

public final class MetricTagSanitizer {
    private MetricTagSanitizer() {
    }

    public static String safeValue(String value) {
        return value == null || value.isBlank() ? CommonMetricTagValues.UNKNOWN : value;
    }
}
