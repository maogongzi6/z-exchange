package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.component.parser.ValueParser;
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

    public SimpleRedisSupport(ValueParser valueParser, BaseRedisSupport<String> baseRedisSupport) {
        super(valueParser, baseRedisSupport);
        this.cacheEncoder = valueParser;
    }

    public <T> Result<Void> set(String key, T value, Duration ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            return Results.fail(CommonErrorCode.PARSE_CACHE_ERROR, "setIfAbsentOrNewer value is null");
        }

        CacheType cacheType = CacheType.fromSource(value);
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
