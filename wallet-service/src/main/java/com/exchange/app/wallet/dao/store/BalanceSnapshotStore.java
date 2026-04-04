package com.exchange.app.wallet.dao.store;

import com.exchange.app.wallet.constant.cache.CacheTtlStrategies;
import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.Results;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BalanceSnapshotStore {
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final StableCacheStrategy<String> balanceSnapshotRefCache;
    private final CacheTtlStrategies cacheTtlStrategies;

    public BalanceSnapshotStore(
            BalanceSnapshotRepository balanceSnapshotRepository,
            @Qualifier("balanceSnapshotRefCache") StableCacheStrategy<String> balanceSnapshotRefCache,
            CacheTtlStrategies cacheTtlStrategies) {
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.balanceSnapshotRefCache = balanceSnapshotRefCache;
        this.cacheTtlStrategies = cacheTtlStrategies;
    }

    public Result<Boolean> cleanNegativeCacheAfterInsert(String refId) {
        Result<Boolean> result = balanceSnapshotRefCache.cleanNegative(refId);
        if (!result.success()) {
            log.error("clean negative cache failed, ref_id: {}, result: {}", refId, result);
        } else if (!result.value()) {
            log.debug("negative cache not cleaned, ref_id: {}, result: {}", refId, result);
        }
        return result;
    }

    public Result<BalanceSnapshot> getByWalletId(String walletId) {
        return Results.success(balanceSnapshotRepository.getByWalletId(walletId));
    }

    // negative value for cache miss, key -> NULL
    // querying by ref id is a user-facing path, so keep a stable negative cache here
    public Result<BalanceSnapshot> getByRefId(String refId) {
        Result<BalanceSnapshot> snapshotResult = doGetByRefId(refId);
        // TODO do not set if get a negative
        if (snapshotResult.success() && snapshotResult.value() == null) {
            Result<Void> result = balanceSnapshotRefCache.setNegative(refId, cacheTtlStrategies.getNegativeStrategy());
            if (!result.success()) {
                log.error("set negative result failed, ref_id: {}, result: {}", refId, result);
            } else {
                log.debug("set negative result, ref_id: {}, result: {}", refId, result);
            }
        }
        return snapshotResult;
    }

    private Result<BalanceSnapshot> doGetByRefId(String refId) {
        Result<CacheValueInfo<String>> refCacheResult = balanceSnapshotRefCache.get(refId);
        if (!refCacheResult.success()) {
            log.error("get cache failed, ref_id: {}, result: {}", refId, refCacheResult);
        } else {
            CacheValueInfo<String> cacheInfo = refCacheResult.value();
            if (CacheValueInfo.ifCacheHit(cacheInfo)) {
                String walletId = cacheInfo.value;
                if (Strings.isEmpty(walletId)) {
                    return Results.success(null);
                }
                return getByWalletId(walletId);
            }
        }

        BalanceSnapshot snapshot = balanceSnapshotRepository.getByWalletReferenceId(refId);
        if (snapshot != null) {
            Result<Void> setRefCacheResult = balanceSnapshotRefCache.set(refId, snapshot.getWalletId(), cacheTtlStrategies.getBalanceSnapshotRefStrategy());
            if (!setRefCacheResult.success()) {
                log.error("set ref cache failed, ref_id: {}, result: {}", refId, setRefCacheResult);
            }
        }
        return Results.success(snapshot);
    }
}
