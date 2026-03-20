package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.common.redis.cache.client.SimpleCacheClient;
import com.exchange.common.redis.cache.strategy.StableCacheAbstract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerRefCache extends StableCacheAbstract<String> {

    public LedgerRefCache(SimpleCacheClient cacheRedisClient) {
        super(cacheRedisClient);
    }

    @Override
    public String getCacheKey(String id) {
        return CacheScope.ledgerRefIdKey(id);
    }

    @Override
    public Class<String> getClazz() {
        return String.class;
    }
}
