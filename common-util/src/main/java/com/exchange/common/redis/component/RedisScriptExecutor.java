package com.exchange.common.redis.component;

import org.redisson.api.RedissonClient;

import java.time.Duration;

public interface RedisScriptExecutor {
    Boolean setIfAbsentOrNewer(RedissonClient redissonClient, String key, String value, long newVersion, Duration ttl);

    String releaseIdempIfOwned(RedissonClient redissonClient, String key, String expectedValue);
}
