package com.exchange.common.redis.idemp;

import com.exchange.common.exception.CacheException;
import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.metrics.MetricTagSanitizer;
import com.exchange.common.redis.idemp.utils.CommonIdempHelper;
import com.exchange.common.redis.idemp.utils.IdempValue;
import com.exchange.common.result.IResult;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.result.error.ErrorCode;
import com.exchange.common.result.error.IdempErrorCode;
import com.exchange.common.result.error.RedisErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DefaultIdempotencyClient implements IdempotencyClient {
    private final IdempRedisClient idempRedisClient;
    private final MeterRegistry meterRegistry;

    @Override
    public Result<Boolean> claimIdempIfAbsent(String service, String scope, String idempId,
                                             String hash, String token, Duration ttl) {
        return downgrade(service, scope, CommonMetricTagValues.IdempotencyOperations.CLAIM, () -> {
            Boolean claimed = idempRedisClient.claimIdempIfAbsent(service, scope, idempId, hash, token, ttl);
            if (claimed == null) {
                return Result.failure(RedisErrorCode.UNEXPECTED_INTERNAL, "idempotency claim returned null");
            }
            return Result.success(claimed);
        });
    }

    @Override
    public Result<Void> markIdempDone(String service, String scope, String idempId,
                                      String hash, String token, String content, Duration ttl) {
        return downgrade(service, scope, CommonMetricTagValues.IdempotencyOperations.MARK_DONE, () -> {
            idempRedisClient.markIdempDone(service, scope, idempId, hash, token, content, ttl);
            return Result.success();
        });
    }

    @Override
    public Result<IdempValue> getIdemp(String service, String scope, String idempId) {
        return downgrade(service, scope, CommonMetricTagValues.IdempotencyOperations.GET, () -> {
            String rawValue = idempRedisClient.getIdemp(service, scope, idempId);
            if (rawValue == null) {
                return Result.success(null);
            }
            return CommonIdempHelper.parseIdempValue(rawValue);
        });
    }

    @Override
    public Result<IdempValue> getIdempAndVerifyHash(String service, String scope,
                                                    String idempId, String expectedHash) {
        Result<IdempValue> getResult = getIdemp(service, scope, idempId);
        if (getResult.isFailed() || getResult.getValue() == null) {
            return getResult;
        }

        IdempValue idempValue = getResult.getValue();
        if (!Objects.equals(idempValue.hash, expectedHash)) {
            Result<IdempValue> result = Result.failure(IdempErrorCode.HASH_CONFLICT, "idempotency hash conflict");
            recordFailure(service, scope, CommonMetricTagValues.IdempotencyOperations.HASH_COMPARE, result);
            return result;
        }
        return getResult;
    }

    @Override
    public Result<String> releaseIdempIfOwned(String service, String scope,
                                              String idempId, String expectedValue) {
        return downgrade(service, scope, CommonMetricTagValues.IdempotencyOperations.RELEASE,
                () -> idempRedisClient.releaseIdempIfOwned(service, scope, idempId, expectedValue));
    }

    @Override
    public Result<Boolean> forceDeleteIdemp(String service, String scope, String idempId) {
        return downgrade(service, scope, CommonMetricTagValues.IdempotencyOperations.FORCE_DELETE, () -> {
            Boolean deleted = idempRedisClient.forceDeleteIdemp(service, scope, idempId);
            if (deleted == null) {
                return Result.failure(RedisErrorCode.UNEXPECTED_INTERNAL, "idempotency force delete returned null");
            }
            return Result.success(deleted);
        });
    }

    private <T> Result<T> downgrade(String service, String scope, String operation, Supplier<Result<T>> invocation) {
        try {
            Result<T> result = invocation.get();
            if (result.isFailed()) {
                recordFailure(service, scope, operation, result);
            }
            return result;
        } catch (CacheException e) {
            Result<T> result = Result.failure(e.getErrorCode(), e.getMessage());
            recordFailure(service, scope, operation, result, e);
            return result;
        } catch (RuntimeException e) {
            Result<T> result = Result.failure(RedisErrorCode.UNEXPECTED_INTERNAL,
                    "idempotency operation failed: " + operation);
            recordFailure(service, scope, operation, result, e);
            return result;
        }
    }

    private void recordFailure(String service, String scope, String operation, IResult<?> result) {
        recordFailure(service, scope, operation, result, null);
    }

    private void recordFailure(String service, String scope, String operation, IResult<?> result, Throwable throwable) {
        String errorType = toErrorType(result.getErrorCode());
        Counter.builder(CommonMetrics.IDEMPOTENCY_ERRORS.name())
                .description(CommonMetrics.IDEMPOTENCY_ERRORS.description())
                .tag(CommonMetricTags.SERVICE, MetricTagSanitizer.safeValue(service))
                .tag(CommonMetricTags.SCOPE, MetricTagSanitizer.safeValue(scope))
                .tag(CommonMetricTags.OPERATION, MetricTagSanitizer.safeValue(operation))
                .tag(CommonMetricTags.ERROR_TYPE, errorType)
                .register(meterRegistry)
                .increment();

        if (throwable == null) {
            log.error("idempotency operation returned failure, service={}, scope={}, operation={}, errorType={}",
                    MetricTagSanitizer.safeValue(service),
                    MetricTagSanitizer.safeValue(scope),
                    MetricTagSanitizer.safeValue(operation),
                    errorType);
            return;
        }
        log.error("idempotency operation failed, service={}, scope={}, operation={}, errorType={}",
                MetricTagSanitizer.safeValue(service),
                MetricTagSanitizer.safeValue(scope),
                MetricTagSanitizer.safeValue(operation),
                errorType,
                throwable);
    }

    private String toErrorType(ErrorCode errorCode) {
        if (errorCode == IdempErrorCode.HASH_CONFLICT) {
            return CommonMetricTagValues.IdempotencyErrorTypes.HASH_CONFLICT;
        }
        if (errorCode == RedisErrorCode.MALFORMED_KEY || errorCode == RedisErrorCode.MALFORMED_VALUE) {
            return CommonMetricTagValues.IdempotencyErrorTypes.PARSE_ERROR;
        }
        if (errorCode == RedisErrorCode.TIMEOUT) {
            return CommonMetricTagValues.IdempotencyErrorTypes.TIMEOUT;
        }
        if (errorCode == RedisErrorCode.ACCESS) {
            return CommonMetricTagValues.IdempotencyErrorTypes.ACCESS;
        }
        if (errorCode == RedisErrorCode.SCRIPT) {
            return CommonMetricTagValues.IdempotencyErrorTypes.SCRIPT_ERROR;
        }
        if (errorCode == RedisErrorCode.CONFIGURATION) {
            return CommonMetricTagValues.IdempotencyErrorTypes.CONFIGURATION_ERROR;
        }
        if (errorCode == RedisErrorCode.CONNECTION) {
            return CommonMetricTagValues.IdempotencyErrorTypes.REDIS_ERROR;
        }
        if (errorCode == RedisErrorCode.UNEXPECTED_INTERNAL) {
            return CommonMetricTagValues.IdempotencyErrorTypes.UNKNOWN;
        }
        if (errorCode == CacheErrorCode.CONTRACT_VIOLATION) {
            return CommonMetricTagValues.IdempotencyErrorTypes.CONTRACT_VIOLATION;
        }
        return CommonMetricTagValues.IdempotencyErrorTypes.UNKNOWN;
    }

}
