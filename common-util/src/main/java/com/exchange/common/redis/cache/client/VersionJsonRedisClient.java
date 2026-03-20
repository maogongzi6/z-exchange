package com.exchange.common.redis.cache.client;

import com.exchange.common.redis.BaseRedisSupport;
import com.exchange.common.redis.cache.helper.CacheValueInfo;
import com.exchange.common.utils.result.Result;
import com.exchange.common.utils.result.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class VersionJsonRedisClient {
    // value should always be a string in the format of "{version|...}"
    private final BaseRedisSupport<String> baseRedisSupport;
    private final CacheReadSupport cacheReadSupport;
    private final VersionCacheWriteSupport versionCacheWriteSupport;

    public <T> Result<CacheValueInfo<T>> get(String key, Class<T> clazz) {
        return cacheReadSupport.get(key, clazz);
    }

    public <T> Result<Boolean> setIfAbsentOrNewer(String key, T value, long newVersion, Duration ttl) {
        return versionCacheWriteSupport.setIfAbsentOrNewer(key, value, newVersion, ttl);
    }

    public Result<Boolean> setTombstone(String key, long newVersion, Duration ttl) {
        return versionCacheWriteSupport.setTombstone(key, newVersion, ttl);
    }

    public Boolean delete(String key) {
        return baseRedisSupport.delete(key);
    }
}
