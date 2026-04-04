package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.constant.cache.CacheTtlStrategies;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.component.support.SimpleCache;
import com.exchange.common.redis.cache.component.support.VersionCache;
import com.exchange.common.redis.cache.model.StrategyOption;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.ops.impl.*;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerRegister {
    @Bean(name = "ledgerRefCache")
    public StableCacheStrategy<String> ledgerRefCache(SimpleCache simpleCache, StrategyFactory factory) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::ledgerRefIdKey);
        ReadOps<String> readOps = new ReadOpsImpl<>(simpleCache, descriptor);
        SimpleWriteOps<String> simpleWriteOps = new WriteOpsImpl<>(simpleCache, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(simpleCache, descriptor);
        return factory.buildStableCacheStrategy(readOps, simpleWriteOps, negativeCacheOps, selfRecoverOps);
    }

    @Bean(name = "ledgerTxnCache")
    public VersionCacheStrategy<LedgerTxn> ledgerTxnCache(VersionCache versionCache, StrategyFactory factory, CacheTtlStrategies ttlStrategies) {
        CacheDescriptor<LedgerTxn> descriptor = new CacheDescriptor<>(LedgerTxn.class, CacheScope::ledgerTxnIdKey);
        ReadOps<LedgerTxn> readOps = new ReadOpsImpl<>(versionCache, descriptor);
        VersionWriteOps<LedgerTxn> versionWriteOps = new VersionWriteOpsImpl<>(versionCache, descriptor);
        VersionTombstoneOps versionTombstoneOps = new VersionTombstoneOpsImpl(versionCache, descriptor);
        VersionNegativeCacheOps versionNegativeCacheOps = new VersionNegativeCacheOpsImpl(versionCache, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(versionCache, descriptor);
        StrategyOption option = new StrategyOption(ttlStrategies.getLedgerTxnOption(), true, false);
        return factory.buildVersionCacheAsideStrategy(readOps, versionWriteOps, versionTombstoneOps, versionNegativeCacheOps, selfRecoverOps, option);
    }
}
