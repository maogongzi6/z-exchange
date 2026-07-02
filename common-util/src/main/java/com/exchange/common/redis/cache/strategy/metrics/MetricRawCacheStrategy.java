package com.exchange.common.redis.cache.strategy.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.redis.cache.model.CacheReadResult;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.result.Result;
import io.micrometer.core.instrument.MeterRegistry;

public class MetricRawCacheStrategy<T> implements RawCacheStrategy<T> {
    private final RawCacheStrategy<T> delegate;
    private final CacheMetricRecorder recorder;

    public MetricRawCacheStrategy(RawCacheStrategy<T> delegate,
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
    public Result<Boolean> afterDbHit(String id, T value) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_DB_HIT, () -> delegate.afterDbHit(id, value));
    }

    @Override
    public Result<Boolean> afterDbMiss(String id) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_DB_MISS, () -> delegate.afterDbMiss(id));
    }

    @Override
    public Result<Boolean> afterUpdate(String id, T value) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_UPDATE, () -> delegate.afterUpdate(id, value));
    }

    @Override
    public Result<Boolean> afterInsert(String id, T value) {
        return record(CommonMetricTagValues.CacheOperations.AFTER_INSERT, () -> delegate.afterInsert(id, value));
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
