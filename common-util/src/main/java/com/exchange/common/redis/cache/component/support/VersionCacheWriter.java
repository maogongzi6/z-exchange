package com.exchange.common.redis.cache.component.support;

import com.exchange.common.exception.CacheException;
import com.exchange.common.redis.aop.CacheExceptionTranslate;
import com.exchange.common.redis.cache.component.codec.VersionCacheEncoder;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.result.error.CacheErrorCode;
import com.exchange.common.utils.TtlStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@CacheExceptionTranslate
public class VersionCacheWriter implements NegativeWriter {
    private final VersionCacheEncoder cacheEncoder;
    private final RedissonClient redissonClient;
    private final ScriptExecutor scriptExecutor;

    public <T> Boolean setIfAbsentOrNewer(String key, T value, long newVersion, TtlStrategy ttl) {
        if (value == null) {
            log.error("setIfAbsentOrNewer value is null, key: {}", key);
            throw new CacheException(CacheErrorCode.CONTRACT_VIOLATION,
                    "setIfAbsentOrNewer value must not be null, key: " + key);
        }
        CacheType cacheType = CacheType.fromSource(value);
        return doSetIfAbsentOrNewer(key, value, cacheType, newVersion, ttl.afterJitter());
    }

    public Boolean setTombstone(String key, long newVersion, TtlStrategy ttl) {
        return doSetIfAbsentOrNewer(key, "", CacheType.TOMBSTONE, newVersion, ttl.afterJitter());
    }

    public void setNegative(String key, TtlStrategy ttl) {
        String encoded = cacheEncoder.encode("", CacheType.NEGATIVE, 0L);
        redissonClient.getBucket(key).setIfAbsent(encoded, ttl.afterJitter());
    }

    private <T> Boolean doSetIfAbsentOrNewer(String key, T value, CacheType cacheType, long newVersion, Duration ttl) {
        String encoded = cacheEncoder.encode(value, cacheType, newVersion);
        return scriptExecutor.setIfAbsentOrNewer(redissonClient, key, encoded, newVersion, ttl);
    }
}
