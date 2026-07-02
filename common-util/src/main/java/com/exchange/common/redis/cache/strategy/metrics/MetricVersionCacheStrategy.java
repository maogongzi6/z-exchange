package com.exchange.common.redis.cache.strategy.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.redis.cache.model.CacheReadResult;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.result.Result;
import io.micrometer.core.instrument.MeterRegistry;

public class MetricVersionCacheStrategy<T> implements VersionCacheStrategy<T> {
    private final VersionCacheStrategy<T> delegate;
    private final CacheMetricRecorder recorder;

    public MetricVersionCacheStrategy(VersionCacheStrategy<T> delegate,
                                      MeterRegistry meterRegistry,
                                      String cacheType) {
        this.delegate = delegate;
        this.recorder = new CacheMetricRecorder(meterRegistry, cacheType);
    }

    @Override
    public Result<CacheReadResult<T>> get(String id) {
        try {
            Result<CacheReadResult<T>> result = delegate.get(id);
            recorder.recordGet(result);
            return result;
        } catch (RuntimeException e) {
            recorder.recordGetException(e);
            throw e;
        }
    }

    @Override
    public Result<Boolean> afterDbHit(String id, T value, long newVersion) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_DB_HIT,
                () -> delegate.afterDbHit(id, value, newVersion));
    }

    @Override
    public Result<Boolean> afterDbMiss(String id) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_DB_MISS, () -> delegate.afterDbMiss(id));
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value, long newVersion) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_UPDATE,
                () -> delegate.afterUpdate(id, value, newVersion));
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value, long newVersion) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_INSERT,
                () -> delegate.afterInsert(id, value, newVersion));
    }

    private Result<Boolean> record(String operation, CacheOperation invocation) {
        try {
            Result<Boolean> result = invocation.execute();
            recorder.recordFailure(operation, result);
            return result;
        } catch (RuntimeException e) {
            recorder.recordException(operation, e);
            throw e;
        }
    }

    @FunctionalInterface
    private interface CacheOperation {
        Result<Boolean> execute();
    }
}
