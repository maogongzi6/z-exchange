package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.redis.cache.model.CacheReadResult;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import com.exchange.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnStore {
    private final LedgerTxnRepository ledgerTxnRepository;
    private final VersionCacheStrategy<LedgerTxn> ledgerTxnCache;
    private final RawCacheStrategy<String> ledgerRefCache;

    public LedgerTxnStore(
            LedgerTxnRepository ledgerTxnRepository,
            @Qualifier("ledgerTxnCache") VersionCacheStrategy<LedgerTxn> ledgerTxnCache,
            @Qualifier("ledgerRefCache") RawCacheStrategy<String> ledgerRefCache) {
        this.ledgerTxnRepository = ledgerTxnRepository;
        this.ledgerTxnCache = ledgerTxnCache;
        this.ledgerRefCache = ledgerRefCache;
    }

    public void postInsert(LedgerTxn ledgerTxn) {
        Result<Boolean> result = ledgerRefCache.afterInsert(ledgerTxn.getReferenceId(), ledgerTxn.getTxnId());
        if (!result.isSuccess()) {
            log.error("clean negative cache failed, ref_id: {}, result: {}", ledgerTxn.getReferenceId(), result);
        }

        result = ledgerTxnCache.afterInsert(ledgerTxn.getTxnId(), ledgerTxn, ledgerTxn.getVersion());
        if (!result.isSuccess()) {
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
        Result<CacheReadResult<LedgerTxn>> cacheResult = ledgerTxnCache.get(txnId);

        if (!cacheResult.isSuccess()) {
            // cache error does not block the main flow
            log.error("cache get failed, txn_id: {}, result: {}", txnId, cacheResult);
        } else {
            CacheReadResult<LedgerTxn> cacheInfo = cacheResult.getValue();
            if (cacheInfo.isValueHit() || cacheInfo.isNegativeHit()) {
                // return target ledger txn if hit a value cache
                // return null to end the query fast if hit a negative cache
                return Result.success(cacheInfo.value());
            }
        }

        // cache miss or tombstone, query db
        LedgerTxn ledgerTxn = ledgerTxnRepository.getByTxnId(txnId);

        // update cache
        if (ledgerTxn != null) {
            Result<Boolean> setResult = ledgerTxnCache.afterDbHit(txnId, ledgerTxn, ledgerTxn.getVersion());
            if (!setResult.isSuccess()) {
                // cache error should not block the main flow
                log.error("ledger_txn_cache, after db hit failed, txn_id: {}, txn: {}, result: {}", txnId, ledgerTxn, setResult);
            }
        } else {
            Result<Boolean> result = ledgerTxnCache.afterDbMiss(txnId);
            if (!result.isSuccess()) {
                log.error("ledger_txn_cache, after db miss failed, txn_id: {}, result: {}", txnId, result);
            }
        }

        return Result.success(ledgerTxn);
    }

    // negative value for cache miss, key -> NULL
    // sometimes querying by ref id is external api call,
    // need to protect external api from random query attack and massive query miss
    // no race condition for ref_id -> txn_id cache, since ref_id is stable in our system
    public Result<LedgerTxn> getByRefId(String refId) {
        Result<CacheReadResult<String>> refCacheResult = ledgerRefCache.get(refId);

        if (!refCacheResult.isSuccess()) {
            // cache error does not block the main flow
            log.error("get cache failed, ref_id: {}, result: {}", refId, refCacheResult);
        } else {
            CacheReadResult<String> cacheInfo = refCacheResult.getValue();
            if (cacheInfo.isNegativeHit()) {
                // Do not renew negative cache TTL on a negative-cache hit.
                return Result.success(null);
            }
            if (cacheInfo.isValueHit()) {
                String txnId = cacheInfo.value();
                Result<LedgerTxn> txnResult = getByTxnId(txnId);
                if (txnResult.isFailed() || txnResult.getValue() != null) {
                    return txnResult;
                }

                log.warn("ref cache hit but txn not found, ref_id: {}, txn_id: {}", refId, txnId);
                return loadByRefIdFromDb(refId);
            }
        }

        return loadByRefIdFromDb(refId);
    }

    private Result<LedgerTxn> loadByRefIdFromDb(String refId) {
        LedgerTxn txn = ledgerTxnRepository.getByRefId(refId);
        if (txn != null) {
            Result<Boolean> setRefCacheResult = ledgerRefCache.afterDbHit(refId, txn.getTxnId());
            if (!setRefCacheResult.isSuccess()) {
                log.error("set ref cache failed, ref_id: {}, result: {}", refId, setRefCacheResult);
            }

            Result<Boolean> setCacheResult = ledgerTxnCache.afterDbHit(txn.getTxnId(), txn, txn.getVersion());
            if (!setCacheResult.isSuccess()) {
                log.error("cache set failed, ref_id: {}, txn_id: {}, txn: {}, result: {}", refId, txn.getTxnId(), txn, setCacheResult);
            }
        } else {
            Result<Boolean> result = ledgerRefCache.afterDbMiss(refId);
            if (!result.isSuccess()) {
                log.error("set negative result failed, ref_id: {}, result: {}", refId, result);
            }
        }
        return Result.success(txn);
    }

    public int updateMetadataByPk(LedgerTxn txn) {
        int affected = ledgerTxnRepository.updateMetadataByPk(txn);
        if (affected == 0) {
            log.debug("update metadata failed, txn_id: {}, txn: {}", txn.getTxnId(), txn);
            return 0;
        }
        Result<Boolean> result = ledgerTxnCache.afterUpdate(txn.getTxnId(), txn, txn.getVersion());
        if (!result.isSuccess()) {
            log.error("ledger_txn_cache, after update failed, txn_id: {}, result: {}", txn.getTxnId(), result);
        }

        return affected;
    }
}
