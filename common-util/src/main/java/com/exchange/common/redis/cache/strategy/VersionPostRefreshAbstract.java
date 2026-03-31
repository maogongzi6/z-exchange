package com.exchange.common.redis.cache.strategy;

import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.impl.VersionPostRefreshStrategy;
import com.exchange.common.utils.TtlStrategy;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public abstract class VersionPostRefreshAbstract<T> extends VersionAbstract<T> implements VersionPostRefreshStrategy<T> {
    private final VersionCacheClient versionedCacheRedisClient;

    public VersionPostRefreshAbstract(VersionCacheClient versionedCacheRedisClient) {
        super(versionedCacheRedisClient);
        this.versionedCacheRedisClient = versionedCacheRedisClient;
    }
    @Override
    public Result<Boolean> setAfterDbCommit(String id, T value, long newVersion, TtlStrategy ttl) {
        return set(id, value, newVersion, ttl);
    }

}
