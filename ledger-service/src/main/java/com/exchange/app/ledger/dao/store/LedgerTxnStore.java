package com.exchange.app.ledger.dao.store;

import com.exchange.app.ledger.config.CustomCacheConfig;
import com.exchange.app.ledger.constant.cache.CacheScope;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.redis.cache.client.StringRedisClient;
import com.exchange.common.redis.cache.client.VersionJsonRedisClient;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.utils.JitterHelper;
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
    private final VersionJsonRedisClient versionedCacheRedisClient;
    private final LedgerTxnRepository ledgerTxnRepository;
    private final CustomCacheConfig.Data dataCacheConfig;
    private final StringRedisClient cacheRedisClient;

    // ledger txn is rarely modified, using a version CAS cache here is like overkill
    // in fact, I'm testing version CAS cache in the ledger txn query,
    // since ledger service is a dummy ledger and its business flow is simple,
    // which is a good place to do tech drill
    public Result<LedgerTxn> getByTxnId(String txnId) {
        String cacheKey = CacheScope.ledgerTxnIdKey(txnId);
        // check cache
        // txn id is usually got from our service and used internally,
        // which means it is low risky to be attacked, and querying non-existed txn is also rare
        // Therefore, do not set negative value (NULL) when cache misses.
        Result<CacheValueInfo<LedgerTxn>> cacheResult = versionedCacheRedisClient.get(cacheKey, LedgerTxn.class);
        if (!cacheResult.success) {
            // cache error should not block the main flow
            log.error("cache get failed, txn_id: {}, result: {}", txnId, cacheResult);
            if (Results.is(cacheResult, CommonErrorCode.PARSE_CACHE_ERROR)) {
                // delete the abnormal value to fast recover
                log.error("invalid cache value, delete to fast recover, txn_id: {}, result: {}", txnId, cacheResult);
                versionedCacheRedisClient.delete(cacheKey);
            }
        }

        LedgerTxn cachedLedger = CacheValueInfo.getValidValue(cacheResult.value);
        if (cacheResult.success && cachedLedger != null) {
            return Results.success(cachedLedger);
        }

        // cache miss, query db
        LedgerTxn ledgerTxn = ledgerTxnRepository.getByTxnId(txnId);

        // update cache
        if (ledgerTxn != null) {
            Result<Boolean> setResult = versionedCacheRedisClient.setIfAbsentOrNewer(cacheKey, ledgerTxn, ledgerTxn.getVersion(),
                    JitterHelper.jitter(dataCacheConfig.getDataCacheTtl(), dataCacheConfig.getJitterMs()));
            if (!setResult.success) {
                // cache error should not block the main flow
                log.error("cache set failed, txn_id: {}, txn: {}, result: {}", txnId, ledgerTxn, setResult);
            } else if (!setResult.value) {
                log.debug("cache not set, txn_id: {}, txn: {}", txnId, ledgerTxn);
            }
        }

        return Results.success(ledgerTxn);
    }

    // TODO add negative value for cache miss, key -> NULL
    //  sometimes query by ref id is external api call,
    //  need to protect external api from random query attack and massive query miss
    // no race condition for ref_id -> txn_id cache, ref_id is stable in our system
    public Result<LedgerTxn> getByRefId(String refId) {
        String refKey = CacheScope.ledgerRefIdKey(refId);
        Result<CacheValueInfo<String>> refCacheResult = cacheRedisClient.get(refKey);
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
            String txnIdKey = CacheScope.ledgerTxnIdKey(txn.getTxnId());
            Result<Void> setRefCacheResult = cacheRedisClient.set(refKey, txn.getTxnId(),
                    JitterHelper.jitter(dataCacheConfig.getIndexCacheTtl(), dataCacheConfig.getJitterMs()));
            if (!setRefCacheResult.success) {
                log.error("set ref cache failed, ref_key: {}, txn_id: {}, txn: {}", refKey, txnId, setRefCacheResult);
            }
            Result<Boolean> setCacheResult = versionedCacheRedisClient.setIfAbsentOrNewer(txnIdKey, txn, txn.getVersion(),
                    JitterHelper.jitter(dataCacheConfig.getDataCacheTtl(), dataCacheConfig.getJitterMs()));
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
        versionedCacheRedisClient.setTombstone(CacheScope.ledgerTxnIdKey(txn.getTxnId()), txn.getVersion(),
                JitterHelper.jitter(dataCacheConfig.getTombstoneTtl(), dataCacheConfig.getJitterMs()));

        return affected;
    }
}
