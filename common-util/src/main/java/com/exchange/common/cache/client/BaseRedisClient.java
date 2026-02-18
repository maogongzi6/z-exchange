package com.exchange.common.cache.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
public abstract class BaseRedisClient<T> {
    final protected RedisTemplate<String, T> redisTemplate;

    protected void set(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    protected void set(String key, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    protected T get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    protected Boolean setIfAbsent(String key, T value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    protected Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}
