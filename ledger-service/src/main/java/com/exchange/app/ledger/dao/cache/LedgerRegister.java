package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.ops.NegativeCacheOps;
import com.exchange.common.redis.cache.ops.SimpleBaseOps;
import com.exchange.common.redis.cache.ops.VersionBaseOps;
import com.exchange.common.redis.cache.ops.VersionTombstoneOps;
import com.exchange.common.redis.cache.ops.impl.NegativeCacheOpsImpl;
import com.exchange.common.redis.cache.ops.impl.SimpleBaseOpsImpl;
import com.exchange.common.redis.cache.ops.impl.VersionBaseOpsImpl;
import com.exchange.common.redis.cache.ops.impl.VersionTombstoneOpsImpl;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.impl.DefaultStableCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.DefaultVersionCacheAsideStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerRegister {
    @Bean(name = "ledgerRefCache")
    public StableCacheStrategy<String> ledgerRefCache(SimpleCacheClient simpleCacheClient) {
        CacheDescriptor<String> descriptor = new CacheDescriptor<>(String.class, CacheScope::ledgerRefIdKey);
        SimpleBaseOps<String> simpleBaseOps = new SimpleBaseOpsImpl<>(simpleCacheClient, descriptor);
        NegativeCacheOps negativeCacheOps = new NegativeCacheOpsImpl(simpleCacheClient, descriptor);
        return new DefaultStableCacheStrategy<>(simpleBaseOps, negativeCacheOps);
    }

    @Bean(name = "ledgerTxnCache")
    public VersionCacheAsideStrategy<LedgerTxn> ledgerTxnCache(VersionCacheClient versionCacheClient) {
        CacheDescriptor<LedgerTxn> descriptor = new CacheDescriptor<>(LedgerTxn.class, CacheScope::ledgerTxnIdKey);
        VersionBaseOps<LedgerTxn> versionBaseOps = new VersionBaseOpsImpl<>(versionCacheClient, descriptor);
        VersionTombstoneOps versionTombstoneOps = new VersionTombstoneOpsImpl(versionCacheClient, descriptor);
        return new DefaultVersionCacheAsideStrategy<>(versionBaseOps, versionTombstoneOps);
    }
}
