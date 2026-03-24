package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.constant.cache.CacheTtlStrategies;
import com.exchange.app.ledger.dao.cache.LedgerRefCache;
import com.exchange.app.ledger.dao.cache.LedgerTxnCache;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerTxnStore {
    private final LedgerTxnRepository ledgerTxnRepository;
    private final LedgerTxnCache ledgerTxnCache;
    private final LedgerRefCache ledgerRefCache;
    private final CacheTtlStrategies cacheTtlStrategies;

    public Result<Void> cleanNegativeCacheAfterInsert(String refId) {
        Result<Void> result = ledgerRefCache.cleanNegative(refId);
        if (!result.success) {
            log.error("clean negative result failed, ref_id: {}, result: {}", refId, result);
        }
        return result;
    }

    // 1. ledger txn is rarely modified, using a version CAS cache here is like overkill
    //    in fact, I'm testing version CAS cache in the ledger txn query,
    //    since ledger service is a dummy ledger and its business flow is simple, which is a good place to do tech drill
    // 2. no need for negative cache, since querying by txn id is used internally,
    //    the cache miss is rare, and attack rick is low
    public Result<LedgerTxn> getByTxnId(String txnId) {
        // check cache
        Result<CacheValueInfo<LedgerTxn>> cacheResult = ledgerTxnCache.get(txnId);

        if (!cacheResult.success) {
            // cache error does not block the main flow
            log.error("cache get failed, txn_id: {}, result: {}", txnId, cacheResult);
        } else {
            CacheValueInfo<LedgerTxn> cacheInfo = cacheResult.value;
            if (CacheValueInfo.ifCacheHit(cacheInfo)) {
                // return target ledger txn if hit a cache
                // return null to end the query fast if hit a negative cache
                return Results.success(cacheInfo.value);
            }
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

    // negative value for cache miss, key -> NULL
    // sometimes querying by ref id is external api call,
    // need to protect external api from random query attack and massive query miss
    public Result<LedgerTxn> getByRefId(String refId) {
        Result<LedgerTxn> txnResult = doGetByRefId(refId);
        if (txnResult.success && txnResult.value == null) {
            Result<Void> result = ledgerRefCache.setNegative(refId, cacheTtlStrategies.getNegativeStrategy());
            if (!result.success) {
                log.error("set negative result failed, ref_id: {}, result: {}", refId, result);
            } else {
                log.debug("set negative result, ref_id: {}, result: {}", refId, result);
            }
        }
        return txnResult;
    }

    // no race condition for ref_id -> txn_id cache, since ref_id is stable in our system
    private Result<LedgerTxn> doGetByRefId(String refId) {
        Result<CacheValueInfo<String>> refCacheResult = ledgerRefCache.get(refId);

        if (!refCacheResult.success) {
            // cache error does not block the main flow
            log.error("get cache failed, ref_id: {}, result: {}", refId, refCacheResult);
        } else {
            CacheValueInfo<String> cacheInfo = refCacheResult.value;
            // return result if cache hit
            if (CacheValueInfo.ifCacheHit(cacheInfo)) {
                String txnId = cacheInfo.value;
                if (Strings.isEmpty(txnId)) {
                    // return null to end the query fast if hit a negative cache
                    return Results.success(null);
                } else {
                    // query target ledger txn if hit a cache
                    Result<LedgerTxn> txnResult = getByTxnId(txnId);
                    if (!txnResult.success) {
                        log.error("load txn failed, ref_id: {}, txn_id: {}, result: {}", refId, txnId, txnResult);
                        return txnResult;
                    }
                    return Results.success(txnResult.value);
                }
            }
        }

        LedgerTxn txn = ledgerTxnRepository.getByRefId(refId);
        if (txn != null) {
            Result<Void> setRefCacheResult = ledgerRefCache.set(refId, txn.getTxnId(), cacheTtlStrategies.getLedgerRefStrategy());
            if (!setRefCacheResult.success) {
                log.error("set ref cache failed, ref_id: {}, result: {}", refId, setRefCacheResult);
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
