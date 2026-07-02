package com.exchange.common.redis.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import com.exchange.common.redis.ClientSideCacheReadSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MeteredClientSideCacheReadSupport<T> implements ClientSideCacheReadSupport<T> {
    private final ClientSideCacheReadSupport<T> delegate;
    private final MeterRegistry meterRegistry;
    private final String service;

    @Override
    public T get(String key) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T value = delegate.get(key);
            stop(sample, CommonMetricTagValues.Outcomes.SUCCESS);
            return value;
        } catch (RuntimeException e) {
            stop(sample, CommonMetricTagValues.Outcomes.ERROR);
            throw e;
        }
    }

    private void stop(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder(CommonMetrics.REDIS_OPERATION_DURATION.name())
                .description(CommonMetrics.REDIS_OPERATION_DURATION.description())
                .tag(CommonMetricTags.SERVICE, MetricTagSanitizer.safeValue(service))
                .tag(CommonMetricTags.COMPONENT, CommonMetricTagValues.RedisComponents.REDISSON_CLIENT_SIDE)
                .tag(CommonMetricTags.OPERATION, CommonMetricTagValues.RedisOperations.GET)
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry));
    }
}
