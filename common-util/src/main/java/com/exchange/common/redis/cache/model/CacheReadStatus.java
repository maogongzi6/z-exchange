package com.exchange.common.redis.cache.model;

public enum CacheReadStatus {
    VALUE_HIT,
    CACHE_MISS,
    NEGATIVE_HIT,
    TOMBSTONE_HIT
}
