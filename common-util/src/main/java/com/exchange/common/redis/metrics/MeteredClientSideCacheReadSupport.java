package com.exchange.common.redis.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.redis.ClientSideCacheReadSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MeteredClientSideCacheReadSupport<T> implements ClientSideCacheReadSupport<T> {
    private final ClientSideCacheReadSupport<T> delegate;
    private final MeterRegistry meterRegistry;
    private final Timer getSuccessTimer;

    public MeteredClientSideCacheReadSupport(ClientSideCacheReadSupport<T> delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        // Client-side cache GET success is expected to be very frequent. Reusing this Timer avoids
        // repeated builder/register lookups and small allocations on every successful read,
        // while the rare error timer path stays straightforward.
        this.getSuccessTimer = Timer.builder(CommonMetrics.REDIS_OPERATION_DURATION.name())
                .description(CommonMetrics.REDIS_OPERATION_DURATION.description())
                .tag(CommonMetricTags.COMPONENT, CommonMetricTagValues.RedisComponents.REDISSON_CLIENT_SIDE)
                .tag(CommonMetricTags.OPERATION, CommonMetricTagValues.RedisOperations.GET)
                .tag(CommonMetricTags.OUTCOME, CommonMetricTagValues.Outcomes.SUCCESS)
                .register(meterRegistry);
    }

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
        if (CommonMetricTagValues.Outcomes.SUCCESS.equals(outcome)) {
            sample.stop(getSuccessTimer);
            return;
        }

        sample.stop(Timer.builder(CommonMetrics.REDIS_OPERATION_DURATION.name())
                .description(CommonMetrics.REDIS_OPERATION_DURATION.description())
                .tag(CommonMetricTags.COMPONENT, CommonMetricTagValues.RedisComponents.REDISSON_CLIENT_SIDE)
                .tag(CommonMetricTags.OPERATION, CommonMetricTagValues.RedisOperations.GET)
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry));
    }
}
