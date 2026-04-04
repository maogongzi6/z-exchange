package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.constant.cache.CacheTtlStrategies;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnStore {
    private final LedgerTxnRepository ledgerTxnRepository;
    private final VersionCacheStrategy<LedgerTxn> ledgerTxnCache;
    private final StableCacheStrategy<String> ledgerRefCache;
    private final CacheTtlStrategies cacheTtlStrategies;

    public LedgerTxnStore(
            LedgerTxnRepository ledgerTxnRepository,
            @Qualifier("ledgerTxnCache") VersionCacheStrategy<LedgerTxn> ledgerTxnCache,
            @Qualifier("ledgerRefCache") StableCacheStrategy<String> ledgerRefCache,
            CacheTtlStrategies cacheTtlStrategies) {
        this.ledgerTxnRepository = ledgerTxnRepository;
        this.ledgerTxnCache = ledgerTxnCache;
        this.ledgerRefCache = ledgerRefCache;
        this.cacheTtlStrategies = cacheTtlStrategies;
    }

    public void postInsert(LedgerTxn ledgerTxn) {
        Result<Boolean> result = ledgerRefCache.cleanNegative(ledgerTxn.getReferenceId());
        if (!result.success()) {
            log.error("clean negative cache failed, ref_id: {}, result: {}", ledgerTxn.getReferenceId(), result);
        } else if (!result.value()) {
            log.error("negative cache not cleaned, ref_id: {}, result: {}", ledgerTxn.getReferenceId(), result);
        }

        result = ledgerTxnCache.afterInsert(ledgerTxn.getTxnId(), ledgerTxn, ledgerTxn.getVersion());
        if (!result.success()) {
            log.error("ledger_txn_cache, after insert failed, txn_id: {}, result: {}", ledgerTxn.getTxnId(), result);
        }
    }

    // 1. ledger txn is rarely modified, using a version CAS cache here is like overkill
    //    in fact, I'm testing version CAS cache in the ledger txn query,
    //    since ledger service is a dummy ledger and its business flow is simple, which is a good place to do tech drill
    // 2. no need for negative cache, since querying by txn id is used internally,
    //    the cache miss is rare, and attack rick is low
    public Result<LedgerTxn> getByTxnId(String txnId) {
        // check cache
        Result<CacheValueInfo<LedgerTxn>> cacheResult = ledgerTxnCache.get(txnId);

        if (!cacheResult.success()) {
            // cache error does not block the main flow
            log.error("cache get failed, txn_id: {}, result: {}", txnId, cacheResult);
        } else {
            CacheValueInfo<LedgerTxn> cacheInfo = cacheResult.value();
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
            Result<Boolean> setResult = ledgerTxnCache.afterDbHit(txnId, ledgerTxn, ledgerTxn.getVersion());
            if (!setResult.success()) {
                // cache error should not block the main flow
                log.error("ledger_txn_cache, after db hit failed, txn_id: {}, txn: {}, result: {}", txnId, ledgerTxn, setResult);
            }
        } else {
            Result<Boolean> result = ledgerTxnCache.afterDbMiss(txnId);
            if (!result.success()) {
                log.error("ledger_txn_cache, after db miss failed, txn_id: {}, result: {}", txnId, result);
            }
        }

        return Results.success(ledgerTxn);
    }

    // negative value for cache miss, key -> NULL
    // sometimes querying by ref id is external api call,
    // need to protect external api from random query attack and massive query miss
    public Result<LedgerTxn> getByRefId(String refId) {
        Result<LedgerTxn> txnResult = doGetByRefId(refId);
        if (txnResult.success() && txnResult.value() == null) {
            // TODO should not set negative if already hit a negative (this could infinitely renewal the negative ttl)
            Result<Void> result = ledgerRefCache.setNegative(refId, cacheTtlStrategies.getNegativeStrategy());
            if (!result.success()) {
                log.error("set negative result failed, ref_id: {}, result: {}", refId, result);
            }
        }
        return txnResult;
    }

    // no race condition for ref_id -> txn_id cache, since ref_id is stable in our system
    private Result<LedgerTxn> doGetByRefId(String refId) {
        Result<CacheValueInfo<String>> refCacheResult = ledgerRefCache.get(refId);

        if (!refCacheResult.success()) {
            // cache error does not block the main flow
            log.error("get cache failed, ref_id: {}, result: {}", refId, refCacheResult);
        } else {
            CacheValueInfo<String> cacheInfo = refCacheResult.value();
            // return result if cache hit
            if (CacheValueInfo.ifCacheHit(cacheInfo)) {
                String txnId = cacheInfo.value;
                if (cacheInfo.cacheType == CacheType.NEGATIVE) {
                    // return null to end the query fast if hit a negative cache
                    return Results.success(null);
                }
                return getByTxnId(txnId);
            }
        }

        LedgerTxn txn = ledgerTxnRepository.getByRefId(refId);
        if (txn != null) {
            Result<Void> setRefCacheResult = ledgerRefCache.set(refId, txn.getTxnId(), cacheTtlStrategies.getLedgerRefStrategy());
            if (!setRefCacheResult.success()) {
                log.error("set ref cache failed, ref_id: {}, result: {}", refId, setRefCacheResult);
            }

            Result<Boolean> setCacheResult = ledgerTxnCache.afterDbHit(txn.getTxnId(), txn, txn.getVersion());
            if (!setCacheResult.success()) {
                log.error("cache set failed, ref_id: {}, txn_id: {}, txn: {}, result: {}", refId, txn.getTxnId(), txn, setCacheResult);
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
        Result<Boolean> result = ledgerTxnCache.afterUpdate(txn.getTxnId(), txn, txn.getVersion());
        if (!result.success()) {
            log.error("ledger_txn_cache, after update failed, txn_id: {}, result: {}", txn.getTxnId(), result);
        }

        return affected;
    }
}
