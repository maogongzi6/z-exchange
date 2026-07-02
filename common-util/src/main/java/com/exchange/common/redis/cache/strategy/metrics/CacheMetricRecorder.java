package com.exchange.common.redis.cache.strategy.metrics;

import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import com.exchange.common.redis.cache.model.CacheReadResult;
import com.exchange.common.redis.cache.model.CacheReadStatus;
import com.exchange.common.result.IResult;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.result.error.ErrorCode;
import com.exchange.common.result.error.RedisErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class CacheMetricRecorder {
    private final MeterRegistry meterRegistry;
    private final String safeCacheType;
    private final Counter valueHitCounter;

    CacheMetricRecorder(MeterRegistry meterRegistry, String cacheType) {
        this.meterRegistry = meterRegistry;
        this.safeCacheType = MetricTagSanitizer.safeValue(cacheType);
        // Cache value hits are the steady-state read path. Reusing this Counter avoids
        // repeated builder/register lookups and small allocations on every successful read,
        // while miss/tombstone/negative/error paths stay simple until profiling says otherwise.
        this.valueHitCounter = Counter.builder(CommonMetrics.CACHE_OPS.name())
                .description(CommonMetrics.CACHE_OPS.description())
                .tag(CommonMetricTags.CACHE_TYPE, safeCacheType)
                .tag(CommonMetricTags.RESULT, CommonMetricTagValues.CacheResults.VALUE_HIT)
                .register(meterRegistry);
    }

    <T> void recordGet(IResult<CacheReadResult<T>> result) {
        if (result == null || result.isFailed()) {
            recordReadResult(CommonMetricTagValues.CacheResults.ERROR);
            recordError(CommonMetricTagValues.CacheOperations.GET, result == null ? null : result.getErrorCode());
            return;
        }

        CacheReadResult<T> readResult = result.getValue();
        if (readResult == null) {
            recordReadResult(CommonMetricTagValues.CacheResults.ERROR);
            recordError(CommonMetricTagValues.CacheOperations.GET, null);
            return;
        }
        recordReadResult(toReadResult(readResult.status()));
    }

    void recordGetException(RuntimeException exception) {
        recordReadResult(CommonMetricTagValues.CacheResults.ERROR);
        recordException(CommonMetricTagValues.CacheOperations.GET, exception);
    }

    void recordFailure(String operation, IResult<?> result) {
        if (result != null && result.isFailed()) {
            recordError(operation, result.getErrorCode());
        }
    }

    void recordException(String operation, RuntimeException exception) {
        ErrorCode errorCode = exception instanceof com.exchange.common.exception.CacheException cacheException
                ? cacheException.getErrorCode()
                : null;
        recordError(operation, errorCode);
    }

    private void recordReadResult(String result) {
        if (CommonMetricTagValues.CacheResults.VALUE_HIT.equals(result)) {
            valueHitCounter.increment();
            return;
        }

        Counter.builder(CommonMetrics.CACHE_OPS.name())
                .description(CommonMetrics.CACHE_OPS.description())
                .tag(CommonMetricTags.CACHE_TYPE, safeCacheType)
                .tag(CommonMetricTags.RESULT, result)
                .register(meterRegistry)
                .increment();
    }

    private void recordError(String operation, ErrorCode errorCode) {
        Counter.builder(CommonMetrics.CACHE_ERRORS.name())
                .description(CommonMetrics.CACHE_ERRORS.description())
                .tag(CommonMetricTags.CACHE_TYPE, safeCacheType)
                .tag(CommonMetricTags.OPERATION, MetricTagSanitizer.safeValue(operation))
                .tag(CommonMetricTags.ERROR_TYPE, toErrorType(errorCode))
                .register(meterRegistry)
                .increment();
    }

    private String toReadResult(CacheReadStatus status) {
        if (status == CacheReadStatus.VALUE_HIT) {
            return CommonMetricTagValues.CacheResults.VALUE_HIT;
        }
        if (status == CacheReadStatus.CACHE_MISS) {
            return CommonMetricTagValues.CacheResults.MISS;
        }
        if (status == CacheReadStatus.TOMBSTONE_HIT) {
            return CommonMetricTagValues.CacheResults.TOMBSTONE_HIT;
        }
        if (status == CacheReadStatus.NEGATIVE_HIT) {
            return CommonMetricTagValues.CacheResults.NEGATIVE_HIT;
        }
        return CommonMetricTagValues.CacheResults.ERROR;
    }

    private String toErrorType(ErrorCode errorCode) {
        if (errorCode == RedisErrorCode.TIMEOUT) {
            return CommonMetricTagValues.CacheErrorTypes.TIMEOUT;
        }
        if (errorCode == RedisErrorCode.ACCESS) {
            return CommonMetricTagValues.CacheErrorTypes.ACCESS;
        }
        if (errorCode == RedisErrorCode.SCRIPT) {
            return CommonMetricTagValues.CacheErrorTypes.SCRIPT_ERROR;
        }
        if (errorCode == RedisErrorCode.CONFIGURATION) {
            return CommonMetricTagValues.CacheErrorTypes.CONFIGURATION_ERROR;
        }
        if (errorCode == RedisErrorCode.CONNECTION) {
            return CommonMetricTagValues.CacheErrorTypes.REDIS_ERROR;
        }
        if (errorCode == RedisErrorCode.MALFORMED_VALUE || errorCode == RedisErrorCode.SERIALIZATION) {
            return CommonMetricTagValues.CacheErrorTypes.DECODE_ERROR;
        }
        if (errorCode == CacheErrorCode.CONTRACT_VIOLATION) {
            return CommonMetricTagValues.CacheErrorTypes.CONTRACT_VIOLATION;
        }
        return CommonMetricTagValues.CacheErrorTypes.UNKNOWN;
    }

}
