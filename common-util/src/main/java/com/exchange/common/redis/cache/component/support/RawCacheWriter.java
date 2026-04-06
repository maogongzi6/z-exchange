package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.CacheEncoder;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class RawCacheWriter implements NegativeWriter {
    private final CacheEncoder cacheEncoder;
    private final BaseRedisSupport<String> baseRedisSupport;

    public <T> Result<Void> set(String key, T value, TtlStrategy ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "setIfAbsentOrNewer value is null");
        }

        CacheType cacheType = CacheType.fromSource(value);
        Result<String> result = encode(value, cacheType);
        if (!result.success()) {
            return Results.fail(result);
        }

        baseRedisSupport.set(key, result.value(), ttl.afterJitter());
        return Results.success();
    }

    // conflict risk without version check
    public Result<Void> setNegative(String key, TtlStrategy ttl) {
        Result<String> result = encode("", CacheType.NEGATIVE);
        if (!result.success()) {
            return Results.fail(result);
        }

        baseRedisSupport.set(key, result.value(), ttl.afterJitter());
        return Results.success();
    }

    private Result<String> encode(Object value, CacheType cacheType) {
        String encoded;
        try {
            encoded = cacheEncoder.encode(value, cacheType);
        } catch (CacheParseException e) {
            log.error("failed to encode value: {}", value, e);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "failed to encode value");
        }
        return Results.success(encoded);
    }
}
