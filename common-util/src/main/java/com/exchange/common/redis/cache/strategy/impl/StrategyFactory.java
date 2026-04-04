package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.redis.cache.model.StrategyOption;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import org.springframework.stereotype.Component;

@Component
public class StrategyFactory {
    public <T> StableCacheStrategy<T> buildStableCacheStrategy(
            ReadOps<T> readOps,
            SimpleWriteOps<T> simpleBaseOps,
            NegativeCacheOps negativeCacheOps,
            SelfRecoverOps selfRecoverOps) {
        return new DefaultStableCacheStrategy<>(readOps, simpleBaseOps, negativeCacheOps, selfRecoverOps);
    }
    
    public <T> VersionCacheStrategy<T> buildVersionCacheAsideStrategy(
            ReadOps<T> readOps,
            VersionWriteOps<T> versionBaseOps,
            VersionTombstoneOps versionTombstoneOps,
            VersionNegativeCacheOps versionNegativeCacheOps,
            SelfRecoverOps selfRecoverOps,
            StrategyOption option
            ) {
        return new DefaultVersionCacheAsideStrategy<>(readOps, versionBaseOps, versionTombstoneOps, versionNegativeCacheOps, selfRecoverOps, option);
    }

    public <T> VersionCacheStrategy<T> buildVersionPostRefreshStrategy(
            ReadOps<T> readOps,
            VersionWriteOps<T> versionBaseOps,
            VersionNegativeCacheOps versionNegativeCacheOps,
            SelfRecoverOps selfRecoverOps,
            StrategyOption option) {
        return new DefaultVersionPostRefreshStrategy<>(readOps, versionBaseOps, versionNegativeCacheOps, selfRecoverOps, option);
    }
}
