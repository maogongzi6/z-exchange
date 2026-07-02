package com.exchange.common.redis.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.redis.RedisValueSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class MeteredRedisValueSupport<T> implements RedisValueSupport<T> {
    private final RedisValueSupport<T> delegate;
    private final MeterRegistry meterRegistry;

    @Override
    public void set(String key, T value) {
        recordVoid(CommonMetricTagValues.RedisOperations.SET, () -> delegate.set(key, value));
    }

    @Override
    public void set(String key, T value, Duration ttl) {
        recordVoid(CommonMetricTagValues.RedisOperations.SET, () -> delegate.set(key, value, ttl));
    }

    @Override
    public T get(String key) {
        return record(CommonMetricTagValues.RedisOperations.GET, () -> delegate.get(key));
    }

    @Override
    public Boolean setIfAbsent(String key, T value, Duration ttl) {
        return record(CommonMetricTagValues.RedisOperations.SET_IF_ABSENT,
                () -> delegate.setIfAbsent(key, value, ttl));
    }

    @Override
    public Boolean delete(String key) {
        return record(CommonMetricTagValues.RedisOperations.DELETE, () -> delegate.delete(key));
    }

    private <R> R record(String operation, Supplier<R> invocation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            R result = invocation.get();
            stop(sample, operation, CommonMetricTagValues.Outcomes.SUCCESS);
            return result;
        } catch (RuntimeException e) {
            stop(sample, operation, CommonMetricTagValues.Outcomes.ERROR);
            throw e;
        }
    }

    private void recordVoid(String operation, Runnable invocation) {
        record(operation, () -> {
            invocation.run();
            return null;
        });
    }

    private void stop(Timer.Sample sample, String operation, String outcome) {
        sample.stop(Timer.builder(CommonMetrics.REDIS_OPERATION_DURATION.name())
                .description(CommonMetrics.REDIS_OPERATION_DURATION.description())
                .tag(CommonMetricTags.COMPONENT, CommonMetricTagValues.RedisComponents.REDIS_TEMPLATE)
                .tag(CommonMetricTags.OPERATION, operation)
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry));
    }
}
