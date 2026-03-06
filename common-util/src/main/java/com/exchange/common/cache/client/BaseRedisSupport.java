package com.exchange.common.cache.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BaseRedisSupport<T> {
    final private RedisTemplate<String, T> redisTemplate;

    // package private
    void set(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    void set(String key, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    T get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    Boolean setIfAbsent(String key, T value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}
