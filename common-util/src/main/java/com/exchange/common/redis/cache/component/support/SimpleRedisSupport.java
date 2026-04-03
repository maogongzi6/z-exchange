package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.codec.impl.ValueCodec;
import com.exchange.common.redis.cache.component.factory.CacheReadSupportFactory;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.component.codec.CacheEncoder;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

// SimpleRedisSupport does not support delete() natively
@Slf4j
public class SimpleRedisSupport {
    private final CacheEncoder cacheEncoder;
    private final BaseRedisSupport<String> baseRedisSupport;
    private final CacheReadSupport simpleRedisReadSupport;

    public SimpleRedisSupport(
            ValueCodec codec,
            BaseRedisSupport<String> baseRedisSupport,
            CacheReadSupportFactory factory) {
        this.cacheEncoder = codec;
        this.baseRedisSupport = baseRedisSupport;
        this.simpleRedisReadSupport = factory.create(codec, baseRedisSupport);
    }

    public Result<CacheValueInfo<String>> get(String key) {
        return simpleRedisReadSupport.get(key);
    }

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        return simpleRedisReadSupport.get(key, clazz);
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

    public Result<Boolean> delete(String key) {
        Boolean deleted = baseRedisSupport.delete(key);
        return Results.success(deleted);
    }
}
