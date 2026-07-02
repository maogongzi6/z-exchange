package com.exchange.common.redis.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import com.exchange.common.redis.component.support.RedisScriptExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class MeteredRedisScriptExecutor implements RedisScriptExecutor {
    private final RedisScriptExecutor delegate;
    private final MeterRegistry meterRegistry;
    private final String service;

    @Override
    public Boolean setIfAbsentOrNewer(RedissonClient redissonClient, String key, String value, long newVersion, Duration ttl) {
        return record(CommonMetricTagValues.RedisOperations.SET_IF_ABSENT_OR_NEWER,
                () -> delegate.setIfAbsentOrNewer(redissonClient, key, value, newVersion, ttl));
    }

    @Override
    public String releaseIdempIfOwned(RedissonClient redissonClient, String key, String expectedValue) {
        return record(CommonMetricTagValues.RedisOperations.RELEASE_IDEMP_IF_OWNED,
                () -> delegate.releaseIdempIfOwned(redissonClient, key, expectedValue));
    }

    private <T> T record(String operation, Supplier<T> invocation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = invocation.get();
            stop(sample, operation, CommonMetricTagValues.Outcomes.SUCCESS);
            return result;
        } catch (RuntimeException e) {
            stop(sample, operation, CommonMetricTagValues.Outcomes.ERROR);
            throw e;
        }
    }

    private void stop(Timer.Sample sample, String operation, String outcome) {
        sample.stop(Timer.builder(CommonMetrics.REDIS_OPERATION_DURATION.name())
                .description(CommonMetrics.REDIS_OPERATION_DURATION.description())
                .tag(CommonMetricTags.SERVICE, MetricTagSanitizer.safeValue(service))
                .tag(CommonMetricTags.COMPONENT, CommonMetricTagValues.RedisComponents.SCRIPT_EXECUTOR)
                .tag(CommonMetricTags.OPERATION, operation)
                .tag(CommonMetricTags.OUTCOME, outcome)
                .register(meterRegistry));
    }
}
