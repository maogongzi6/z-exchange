package com.exchange.app.ledger.dao.cache;

import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.client.VersionCacheClient;
import com.exchange.common.redis.cache.strategy.VersionCacheAsideAbstract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnCache extends VersionCacheAsideAbstract<LedgerTxn> {
    public LedgerTxnCache(VersionCacheClient versionedCacheRedisClient) {
        super(versionedCacheRedisClient);
    }

    @Override
    public String getCacheKey(String txnId) {
        return CacheScope.ledgerTxnIdKey(txnId);
    }

    @Override
    public Class<LedgerTxn> getClazz() {
        return LedgerTxn.class;
    }
}
