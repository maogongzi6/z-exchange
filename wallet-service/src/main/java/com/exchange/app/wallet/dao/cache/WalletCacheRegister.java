package com.exchange.app.wallet.dao.cache;

import com.exchange.app.wallet.constant.cache.CacheScope;
import com.exchange.app.wallet.constant.cache.CacheTtlStrategies;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.redis.cache.component.support.ReadableCache;
import com.exchange.common.redis.cache.component.support.SimpleCache;
import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.model.StrategyOption;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.ops.impl.*;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletCacheRegister {
    @Bean(name = "balanceSnapshotRefCache")
    public StableCacheStrategy<String> balanceSnapshotRefCache(
            SimpleCache simpleCache,
            StrategyFactory factory) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::balanceSnapshotRefIdKey);
        ReadOps<String> readOps = new ReadOpsImpl<>(simpleCache, descriptor);
        SimpleWriteOps<String> simpleWriteOps = new WriteOpsImpl<>(simpleCache, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(simpleCache, descriptor);
        return factory.buildStableCacheStrategy(readOps, simpleWriteOps, negativeCacheOps, selfRecoverOps);
    }

    @Bean("normalSnapshotCache")
    public VersionCacheStrategy<BalanceSnapshot> normalSnapshotCache(
            @Qualifier("defaultReadableCache") ReadableCache defaultReadableCache,
            VersionCache versionCache,
            StrategyFactory factory,
            CacheTtlStrategies cacheTtlStrategies) {
        CacheDescriptor<BalanceSnapshot> descriptor = new CacheDescriptor<>(BalanceSnapshot.class, CacheScope::balanceSnapshotIdKey);
        ReadOps<BalanceSnapshot> readOps = new ReadOpsImpl<>(defaultReadableCache, descriptor);
        VersionWriteOps<BalanceSnapshot> versionWriteOps = new VersionWriteOpsImpl<>(versionCache, descriptor);
        VersionNegativeCacheOps negativeCacheOps = new VersionNegativeCacheOpsImpl(versionCache, descriptor);
        VersionTombstoneOps versionTombstoneOps = new VersionTombstoneOpsImpl(versionCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(versionCache, descriptor);
        StrategyOption option = new StrategyOption(cacheTtlStrategies.getNormalSnapshotOption(), true, true);
        return factory.buildVersionCacheAsideStrategy(
                readOps,
                versionWriteOps,
                versionTombstoneOps,
                negativeCacheOps,
                selfRecoverOps,
                option
        );
    }

    @Bean("hotSnapshotCache")
    public VersionCacheStrategy<BalanceSnapshot> hotSnapshotCache(
            @Qualifier("clientSideReadableCache") ReadableCache clientSideReadableCache,
            VersionCache versionCache,
            StrategyFactory factory,
            CacheTtlStrategies cacheTtlStrategies) {
        CacheDescriptor<BalanceSnapshot> descriptor = new CacheDescriptor<>(BalanceSnapshot.class, CacheScope::balanceSnapshotIdKey);
        ReadOps<BalanceSnapshot> readOps = new ReadOpsImpl<>(clientSideReadableCache, descriptor);
        VersionWriteOps<BalanceSnapshot> versionWriteOps = new VersionWriteOpsImpl<>(versionCache, descriptor);
        VersionNegativeCacheOps negativeCacheOps = new VersionNegativeCacheOpsImpl(versionCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(versionCache, descriptor);
        StrategyOption option = new StrategyOption(cacheTtlStrategies.getHotSnapshotOption(), false, true);
        return factory.buildVersionPostRefreshStrategy(
                readOps,
                versionWriteOps,
                negativeCacheOps,
                selfRecoverOps,
                option
        );
    }
}
