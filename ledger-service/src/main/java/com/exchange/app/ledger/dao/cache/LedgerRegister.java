package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.VersionStrategy;
import com.exchange.common.redis.cache.strategy.discriptor.CacheDescriptor;
import com.exchange.common.redis.cache.strategy.impl.DefaultStableCacheStrategy;
import com.exchange.common.redis.cache.strategy.impl.DefaultVersionCacheAsideStrategy;
import com.exchange.common.redis.cache.strategy.impl.DefaultVersionStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerRegister {
    @Bean(name = "ledgerRefCache")
    public StableCacheStrategy<String> ledgerRefCache(SimpleCacheClient simpleCacheClient) {
        return new DefaultStableCacheStrategy<>(simpleCacheClient,
                new CacheDescriptor<>(String.class, CacheScope::ledgerRefIdKey));
    }

    @Bean(name = "ledgerTxnCache")
    public VersionCacheAsideStrategy<LedgerTxn> ledgerTxnCache(VersionCacheClient versionCacheClient) {
        CacheDescriptor<LedgerTxn> cacheDescriptor = new CacheDescriptor<>(LedgerTxn.class, CacheScope::ledgerTxnIdKey);
        VersionStrategy<LedgerTxn> versionStrategy = new DefaultVersionStrategy<>(versionCacheClient, cacheDescriptor);
        return new DefaultVersionCacheAsideStrategy<>(versionCacheClient, versionStrategy, cacheDescriptor);
    }
}
