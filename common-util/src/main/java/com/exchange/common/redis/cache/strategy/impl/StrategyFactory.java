package com.exchange.common.redis.cache.strategy.impl;

import com.exchange.common.component.PromotionClassifier;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.ops.impl.L1L2ReadOpsImpl;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
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
            SelfRecoverOps selfRecoverOps) {
        return new DefaultVersionCacheAsideStrategy<>(readOps, versionBaseOps, versionTombstoneOps, versionNegativeCacheOps, selfRecoverOps);
    }

    public <T> VersionPostRefreshStrategy<T> buildVersionPostRefreshStrategy(
            ReadOps<T> readOps,
            VersionWriteOps<T> versionBaseOps,
            VersionNegativeCacheOps versionNegativeCacheOps,
            SelfRecoverOps selfRecoverOps) {
        return new DefaultVersionPostRefreshStrategy<>(readOps, versionBaseOps, versionNegativeCacheOps, selfRecoverOps);
    }

    public <T> VersionCacheStrategy<T> buildL1L2VersionCacheAsideStrategy(
            ReadOps<T> l1ReadOps,
            ReadOps<T> l2ReadOps,
            PromotionClassifier classifier,
            VersionWriteOps<T> versionBaseOps,
            VersionTombstoneOps versionTombstoneOps,
            VersionNegativeCacheOps versionNegativeCacheOps,
            SelfRecoverOps selfRecoverOps) {
        L1L2ReadOpsImpl<T> readOps = new L1L2ReadOpsImpl<>(l1ReadOps, l2ReadOps, classifier);
        return new DefaultVersionCacheAsideStrategy<>(readOps, versionBaseOps, versionTombstoneOps, versionNegativeCacheOps, selfRecoverOps);
    }

    public <T> VersionPostRefreshStrategy<T> buildL1L2VersionPostRefreshStrategy(
            ReadOps<T> l1ReadOps,
            ReadOps<T> l2ReadOps,
            PromotionClassifier classifier,
            VersionWriteOps<T> versionBaseOps,
            VersionNegativeCacheOps versionNegativeCacheOps,
            SelfRecoverOps selfRecoverOps) {
        L1L2ReadOpsImpl<T> readOps = new L1L2ReadOpsImpl<>(l1ReadOps, l2ReadOps, classifier);
        return new DefaultVersionPostRefreshStrategy<>(readOps, versionBaseOps, versionNegativeCacheOps, selfRecoverOps);
    }
}
