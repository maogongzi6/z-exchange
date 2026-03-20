package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.config.CustomCacheConfig;
import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.constant.cache.CacheTtlStrategies;
import com.exchange.app.ledger.dao.cache.LedgerRefCache;
import com.exchange.app.ledger.dao.cache.LedgerTxnCache;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.JitterHelper;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LedgerTxnStore {
    private final LedgerTxnRepository ledgerTxnRepository;
    private final LedgerTxnCache ledgerTxnCache;
    private final LedgerRefCache ledgerRefCache;
    private final CacheTtlStrategies cacheTtlStrategies;

    // ledger txn is rarely modified, using a version CAS cache here is like overkill
    // in fact, I'm testing version CAS cache in the ledger txn query,
    // since ledger service is a dummy ledger and its business flow is simple,
    // which is a good place to do tech drill
    public Result<LedgerTxn> getByTxnId(String txnId) {
        // check cache
        // txn id is usually got from our service and used internally,
        // which means it is low risky to be attacked, and querying non-existed txn is also rare
        // Therefore, do not set negative value (NULL) when cache misses.
        Result<CacheValueInfo<LedgerTxn>> cacheResult = ledgerTxnCache.get(txnId);

        LedgerTxn cachedLedger = CacheValueInfo.getValidValue(cacheResult.value);
        if (cacheResult.success && cachedLedger != null) {
            return Results.success(cachedLedger);
        }

        // cache miss, query db
        LedgerTxn ledgerTxn = ledgerTxnRepository.getByTxnId(txnId);

        // update cache
        if (ledgerTxn != null) {
            Result<Boolean> setResult = ledgerTxnCache.setCacheAside(txnId, ledgerTxn, ledgerTxn.getVersion(), cacheTtlStrategies.getLedgerTxnStrategy());
            if (!setResult.success) {
                // cache error should not block the main flow
                log.error("cache set failed, txn_id: {}, txn: {}, result: {}", txnId, ledgerTxn, setResult);
            }
        }

        return Results.success(ledgerTxn);
    }

    // TODO add negative value for cache miss, key -> NULL
    //  sometimes query by ref id is external api call,
    //  need to protect external api from random query attack and massive query miss
    // no race condition for ref_id -> txn_id cache, ref_id is stable in our system
    public Result<LedgerTxn> getByRefId(String refId) {
        Result<CacheValueInfo<String>> refCacheResult = ledgerRefCache.get(refId);
        String txnId = CacheValueInfo.getValidValue(refCacheResult.value);
        LedgerTxn txn;
        if (refCacheResult.success && txnId != null) {
            Result<LedgerTxn> txnResult = getByTxnId(txnId);
            if (!txnResult.success) {
                log.error("load txn failed, ref_id: {}, txn_id: {}, result: {}", refId, txnId, txnResult);
                return txnResult;
            }
            return Results.success(txnResult.value);
        }

        txn = ledgerTxnRepository.getByRefId(refId);
        if (txn != null) {
            Result<Void> setRefCacheResult = ledgerRefCache.set(refId, txn.getTxnId(), cacheTtlStrategies.getLedgerRefStrategy());
            if (!setRefCacheResult.success) {
                log.error("set ref cache failed, ref_key: {}, txn_id: {}, txn: {}", refId, txnId, setRefCacheResult);
            }
            Result<Boolean> setCacheResult = ledgerTxnCache.setCacheAside(txn.getTxnId(), txn, txn.getVersion(), cacheTtlStrategies.getLedgerTxnStrategy());
            if (!setCacheResult.success) {
                log.error("cache set failed, ref_id: {}, txn_id: {}, txn: {}, result: {}", refId, txn.getTxnId(), txn, setCacheResult);
            } else if (!setCacheResult.value) {
                log.debug("cache not set, ref_id: {}, txn_id: {}, txn: {}", refId, txn.getTxnId(), txn);
            }
        }
        return Results.success(txn);
    }

    public int updateMetadataByPk(LedgerTxn txn) {
        int affected = ledgerTxnRepository.updateMetadataByPk(txn);
        if (affected == 0) {
            log.debug("update metadata failed, txn_id: {}, txn: {}", txn.getTxnId(), txn);
            return 0;
        }
        ledgerTxnCache.setTombstoneAfterWrite(txn.getTxnId(), txn.getVersion(), cacheTtlStrategies.getTombstoneStrategy());

        return affected;
    }
}
