package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.config.CustomCacheConfig;
import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.cache.client.JsonVersionedCacheRedisClient;
import com.exchange.common.utils.result.CommonErrorCode;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LedgerTxnStore {
    private final JsonVersionedCacheRedisClient versionedCacheRedisClient;
    private final LedgerTxnRepository ledgerTxnRepository;
    private final CustomCacheConfig.Data dataCacheConfig;

    public Result<LedgerTxn> getTxnByTxnId(String txnId) {
        String cacheKey = CacheScope.ledgerTxnIdKey(txnId);
        // check cache
        // txn id is usually got from our service and used internally,
        // which means it is low risky to be attacked, and querying non-existed txn is also rare
        // Therefore, do not set negative value (NULL) when cache misses.
        Result<LedgerTxn> cacheResult = versionedCacheRedisClient.get(cacheKey, LedgerTxn.class);
        if (!cacheResult.success) {
            // cache error should not block the main flow
            log.error("cache get failed, txn_id: {}, result: {}", txnId, cacheResult);
            if (Results.is(cacheResult, CommonErrorCode.INVALID_VERSIONED_CACHE_VALUE)) {
                // delete the abnormal value to fast recover
                log.error("invalid cache value, delete to fast recover, txn_id: {}, result: {}", txnId, cacheResult);
                versionedCacheRedisClient.delete(cacheKey);
            }
        }
        if (cacheResult.success && cacheResult.value != null) {
            return cacheResult;
        }

        // cache miss, query db
        LedgerTxn ledgerTxn = ledgerTxnRepository.getByTxnId(txnId);

        // update cache
        if (ledgerTxn != null) {
            Result<Boolean> setResult = versionedCacheRedisClient.setIfAbsentOrNewer(cacheKey, ledgerTxn, ledgerTxn.getVersion(), dataCacheConfig.getDataCacheTtl());
            if (!setResult.success) {
                // cache error should not block the main flow
                log.error("cache set failed, txn_id: {}, txn: {}, result: {}", txnId, ledgerTxn, setResult);
            } else if (!setResult.value) {
                log.debug("cache not set, txn_id: {}, txn: {}", txnId, ledgerTxn);
            }
        }

        return Results.success(ledgerTxn);
    }

    public int updateMetadataByPk(LedgerTxn txn) {
        int affected = ledgerTxnRepository.updateMetadataByPk(txn);
        if (affected == 0) {
            log.debug("update metadata failed, txn_id: {}, txn: {}", txn.getTxnId(), txn);
            return 0;
        }
        versionedCacheRedisClient.setTombstone(
                CacheScope.ledgerTxnIdKey(txn.getTxnId()),
                txn.getVersion(),
                dataCacheConfig.getTombstoneTtl()
        );

        return affected;
    }
}
