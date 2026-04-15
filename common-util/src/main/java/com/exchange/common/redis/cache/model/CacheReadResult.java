package com.exchange.common.redis.cache.model;

import java.util.Objects;

public record CacheReadResult<T>(T value, CacheReadStatus status, long version) {
    public static final long NO_VERSION = -1L;

    public CacheReadResult {
        Objects.requireNonNull(status, "status");
    }

    public static <T> CacheReadResult<T> valueHit(T value) {
        return new CacheReadResult<>(value, CacheReadStatus.VALUE_HIT, NO_VERSION);
    }

    public static <T> CacheReadResult<T> valueHit(T value, long version) {
        return new CacheReadResult<>(value, CacheReadStatus.VALUE_HIT, version);
    }

    public static <T> CacheReadResult<T> miss() {
        return new CacheReadResult<>(null, CacheReadStatus.CACHE_MISS, NO_VERSION);
    }

    public static <T> CacheReadResult<T> negativeHit() {
        return new CacheReadResult<>(null, CacheReadStatus.NEGATIVE_HIT, NO_VERSION);
    }

    public static <T> CacheReadResult<T> negativeHit(long version) {
        return new CacheReadResult<>(null, CacheReadStatus.NEGATIVE_HIT, version);
    }

    public static <T> CacheReadResult<T> tombstoneHit() {
        return new CacheReadResult<>(null, CacheReadStatus.TOMBSTONE_HIT, NO_VERSION);
    }

    public static <T> CacheReadResult<T> tombstoneHit(long version) {
        return new CacheReadResult<>(null, CacheReadStatus.TOMBSTONE_HIT, version);
    }

    public boolean isValueHit() {
        return status == CacheReadStatus.VALUE_HIT;
    }

    public boolean isCacheMiss() {
        return status == CacheReadStatus.CACHE_MISS;
    }

    public boolean isNegativeHit() {
        return status == CacheReadStatus.NEGATIVE_HIT;
    }

    public boolean isTombstoneHit() {
        return status == CacheReadStatus.TOMBSTONE_HIT;
    }

    public boolean shouldQueryDb() {
        return isCacheMiss() || isTombstoneHit();
    }
}
