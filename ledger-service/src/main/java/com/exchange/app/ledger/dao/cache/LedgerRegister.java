package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.component.support.SimpleRedisSupport;
import com.exchange.common.redis.cache.component.support.VersionedRedisSupport;
import com.exchange.common.redis.cache.ops.*;
import com.exchange.common.redis.cache.ops.impl.*;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.impl.DefaultStableCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.DefaultVersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.impl.StrategyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerRegister {
    @Bean(name = "ledgerRefCache")
    public StableCacheStrategy<String> ledgerRefCache(SimpleRedisSupport simpleRedisSupport, StrategyFactory factory) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::ledgerRefIdKey);
        ReadOps<String> readOps = new ReadOpsImpl<>(simpleRedisSupport, descriptor);
        SimpleWriteOps<String> simpleWriteOps = new WriteOpsImpl<>(simpleRedisSupport, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleRedisSupport, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(simpleRedisSupport, descriptor);
        return factory.buildStableCacheStrategy(readOps, simpleWriteOps, negativeCacheOps, selfRecoverOps);
    }

    @Bean(name = "ledgerTxnCache")
    public VersionCacheAsideStrategy<LedgerTxn> ledgerTxnCache(VersionedRedisSupport versionedRedisSupport, StrategyFactory factory) {
        CacheDescriptor<LedgerTxn> descriptor = new CacheDescriptor<>(LedgerTxn.class, CacheScope::ledgerTxnIdKey);
        ReadOps<LedgerTxn> readOps = new ReadOpsImpl<>(versionedRedisSupport, descriptor);
        VersionWriteOps<LedgerTxn> versionWriteOps = new VersionWriteOpsImpl<>(versionedRedisSupport, descriptor);
        VersionTombstoneOps versionTombstoneOps = new VersionTombstoneOpsImpl(versionedRedisSupport, descriptor);
        VersionNegativeCacheOps versionNegativeCacheOps = new VersionNegativeCacheOpsImpl(versionedRedisSupport, descriptor);
        SelfRecoverOps selfRecoverOps = new RawDeleteRecoverOpsImpl(versionedRedisSupport, descriptor);
        return factory.buildVersionCacheAsideStrategy(readOps, versionWriteOps, versionTombstoneOps, versionNegativeCacheOps, selfRecoverOps);
    }
}
