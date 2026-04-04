package com.exchange.app.wallet.dao.cache;

import com.exchange.app.wallet.constant.cache.CacheScope;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.component.PromotionClassifier;
import com.exchange.common.redis.cache.component.support.ReadableCache;
import com.exchange.common.redis.cache.component.support.SimpleCache;
import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.ops.impl.*;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionPostRefreshStrategy;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletCacheRegister {
    @Bean(name = "balanceSnapshotRefCache")
    public StableCacheStrategy<String> balanceSnapshotRefCache(
            SimpleCache simpleRedisSupport,
            StrategyFactory factory) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::balanceSnapshotRefIdKey);
        ReadOps<String> readOps = new ReadOpsImpl<>(simpleRedisSupport, descriptor);
        SimpleWriteOps<String> simpleWriteOps = new WriteOpsImpl<>(simpleRedisSupport, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleRedisSupport, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(simpleRedisSupport, descriptor);
        return factory.buildStableCacheStrategy(readOps, simpleWriteOps, negativeCacheOps, selfRecoverOps);
    }

    @Bean("hotSnapshotCache")
    public VersionPostRefreshStrategy<BalanceSnapshot> balanceSnapshotCache(
            @Qualifier("clientSideReadableCache") ReadableCache clientSideReadableCache,
            VersionCache versionCache,
            @Qualifier("hotBalanceSnapshotClassifier") PromotionClassifier classifier,
            StrategyFactory factory) {
        CacheDescriptor<BalanceSnapshot> descriptor = new CacheDescriptor<>(BalanceSnapshot.class, CacheScope::balanceSnapshotIdKey);
        ReadOps<BalanceSnapshot> l1ReadOps = new ReadOpsImpl<>(clientSideReadableCache, descriptor);
        ReadOps<BalanceSnapshot> l2ReadOps = new ReadOpsImpl<>(versionCache, descriptor);

        VersionWriteOps<BalanceSnapshot> versionWriteOps = new VersionWriteOpsImpl<>(versionCache, descriptor);
        VersionNegativeCacheOps negativeCacheOps = new VersionNegativeCacheOpsImpl(versionCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(versionCache, descriptor);
        return factory.buildL1L2VersionPostRefreshStrategy(
                l1ReadOps,
                l2ReadOps,
                classifier,
                versionWriteOps,
                negativeCacheOps,
                selfRecoverOps
        );
    }
}
