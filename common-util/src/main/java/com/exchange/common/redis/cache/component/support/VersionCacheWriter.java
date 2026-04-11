package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheParseException;
import com.exchange.common.result.Result;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.redis.cache.component.codec.VersionCacheEncoder;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.utils.TtlStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class VersionCacheWriter implements NegativeWriter {
    private final VersionCacheEncoder cacheEncoder;
    private final RedissonClient redissonClient;
    private final ScriptExecutor scriptExecutor;

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T value, long newVersion, TtlStrategy ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            return Result.failure(CacheErrorCode.PARSE_CACHE_ERROR, "setIfAbsentOrNewer value is null");
        }
        CacheType cacheType = CacheType.fromSource(value);
        return doSetIfAbsentOrNewer(key, value, cacheType, newVersion, ttl.afterJitter());
    }

    public Result<Boolean> setTombstone(String key, long newVersion, TtlStrategy ttl) {
        return doSetIfAbsentOrNewer(key, "", CacheType.TOMBSTONE, newVersion, ttl.afterJitter());
    }

    public Result<Void> setNegative(String key, TtlStrategy ttl) {
        String encoded;
        try {
            encoded = cacheEncoder.encode("", CacheType.NEGATIVE, 0L);
        } catch (CacheParseException e) {
            log.error("failed to encode negative cache, key: {}", key, e);
            return Result.failure(CacheErrorCode.PARSE_CACHE_ERROR, "failed to encode negative cache, key: " + key);
        }
        redissonClient.getBucket(key).setIfAbsent(encoded, ttl.afterJitter());
        return Result.success();
    }

    private <T> Result<Boolean> doSetIfAbsentOrNewer(String key, T value, CacheType cacheType, long newVersion, Duration ttl) {
        String encoded;
        try {
            encoded = cacheEncoder.encode(value, cacheType, newVersion);
        } catch (CacheParseException e) {
            log.error("failed to encode value, key: {}, value: {}", key, value, e);
            return Result.failure(CacheErrorCode.PARSE_CACHE_ERROR, "failed to encode value, key: " + key);
        }

        return scriptExecutor.setIfAbsentOrNewer(redissonClient, key, encoded, newVersion, ttl);
    }

}
