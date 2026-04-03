package com.exchange.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class BaseRedisSupport<T> implements BaseCacheReadSupport<T> {
    final private RedisTemplate<String, T> redisTemplate;

    // package private
    public void set(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public T get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean setIfAbsent(String key, T value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}
