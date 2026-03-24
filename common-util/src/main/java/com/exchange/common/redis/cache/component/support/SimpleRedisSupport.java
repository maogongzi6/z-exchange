package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.ValueCodec;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.component.impl.CacheEncoder;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class SimpleRedisSupport extends ReadRedisSupport {
    private final CacheEncoder cacheEncoder;

    public SimpleRedisSupport(ValueCodec codec, BaseRedisSupport<String> baseRedisSupport) {
        super(codec, baseRedisSupport);
        this.cacheEncoder = codec;
    }

    public <T> Result<Void> set(String key, T value, Duration ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "setIfAbsentOrNewer value is null");
        }

        CacheType cacheType = CacheType.fromSource(value);
        return doSet(key, value, cacheType, ttl);
    }

    // conflict risk without version check
    public Result<Void> setNegative(String key, Duration ttl) {
        return doSet(key, "", CacheType.NEGATIVE, ttl);
    }

    public Result<Void> doSet(String key, Object value, CacheType cacheType, Duration ttl) {
        String encoded;
        try {
            encoded = cacheEncoder.encode(value, cacheType);
        } catch (CacheParseException e) {
            log.error("failed to encode value, key: {}, value: {}", key, value, e);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "failed to encode value, key: " + key);
        }
        baseRedisSupport.set(key, encoded, ttl);
        return Results.success();
    }
}
