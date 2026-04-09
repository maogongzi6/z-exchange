package com.exchange.app.wallet.dao.store;

import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.component.PromotionClassifier;
import com.exchange.common.result.Result;
import com.exchange.common.redis.cache.constant.CacheType;
import com.exchange.common.redis.cache.model.CacheValueInfo;
import com.exchange.common.redis.cache.strategy.RawCacheStrategy;
import com.exchange.common.redis.cache.strategy.VersionCacheStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BalanceSnapshotStore {
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    // TODO hot
    private final RawCacheStrategy<String> balanceSnapshotRefCache;
    private final VersionCacheStrategy<BalanceSnapshot> hotSnapshotCache;
    private final VersionCacheStrategy<BalanceSnapshot> normalSnapshotCache;
    private final PromotionClassifier promotionClassifier;

    public BalanceSnapshotStore(
            BalanceSnapshotRepository balanceSnapshotRepository,
            @Qualifier("balanceSnapshotRefCache") RawCacheStrategy<String> balanceSnapshotRefCache,
            @Qualifier("hotSnapshotCache") VersionCacheStrategy<BalanceSnapshot> hotSnapshotCache,
            @Qualifier("normalSnapshotCache") VersionCacheStrategy<BalanceSnapshot> normalSnapshotCache,
            @Qualifier("hotBalanceSnapshotClassifier") PromotionClassifier promotionClassifier) {
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.balanceSnapshotRefCache = balanceSnapshotRefCache;
        this.hotSnapshotCache = hotSnapshotCache;
        this.normalSnapshotCache = normalSnapshotCache;
        this.promotionClassifier = promotionClassifier;
    }

    public void postInsert(BalanceSnapshot balanceSnapshot) {
        postInsert(balanceSnapshot.getWalletId(), balanceSnapshot.getWalletReferenceId(), balanceSnapshot.getOwnerType());
        Result<Boolean> result = determineCacheStrategy(balanceSnapshot.getWalletId()).afterInsert(balanceSnapshot.getWalletId(), balanceSnapshot, balanceSnapshot.getVersion());
        if (!result.isSuccess()) {
            log.error("after_insert, cache strategy failed: {}, snapshot: {}", result, balanceSnapshot);
        }
    }

    public void postInsert(String walletId, String refId, OwnerType ownerType) {
        promoteHotSnapshot(walletId, ownerType);

        // clean negative ref cache
        Result<Boolean> refResult = balanceSnapshotRefCache.afterInsert(refId, walletId);
        if (!refResult.isSuccess()) {
            log.error("clean negative cache failed, ref_id: {}, refResult: {}", refId, refResult);
        }
    }

    public Result<BalanceSnapshot> getByWalletId(String walletId) {
        var result = doGetByWalletId(walletId);
        if (result.isSuccess() && result.getValue() != null) {
            BalanceSnapshot balanceSnapshot = result.getValue();
            promoteHotSnapshot(balanceSnapshot.getWalletId(), balanceSnapshot.getOwnerType());
        }
        return result;
    }

    public Result<BalanceSnapshot> doGetByWalletId(String walletId) {
        var snapshotCache = determineCacheStrategy(walletId);

        Result<CacheValueInfo<BalanceSnapshot>> cacheResult = snapshotCache.get(walletId);
        if (!cacheResult.isSuccess()) {
            log.error("getByWalletId cache failed, walletId: {}, result: {}", walletId, cacheResult);
        } else {
            CacheValueInfo<BalanceSnapshot> valueInfo = cacheResult.getValue();
            if (CacheValueInfo.ifCacheHit(valueInfo)) {
                return Result.success(valueInfo.value);
            }
        }

        BalanceSnapshot balanceSnapshot = balanceSnapshotRepository.getByWalletId(walletId);
        if (balanceSnapshot != null) {
            Result<Boolean> setResult = snapshotCache.afterDbHit(balanceSnapshot.getWalletId(), balanceSnapshot, balanceSnapshot.getVersion());
            if (!setResult.isSuccess()) {
                log.error("getByWalletId|afterDbHit cache failed, snapshot: {}, result: {}", balanceSnapshot, setResult);
            }
        } else {
            Result<Boolean> setResult = snapshotCache.afterDbMiss(walletId);
            if (!setResult.isSuccess()) {
                log.error("afterDbMiss cache failed, walletId: {}, result: {}", walletId, setResult);
            }
        }

        return Result.success(balanceSnapshot);
    }

    // negative value for cache miss, key -> NULL
    // querying by ref id is a user-facing path, so keep a stable negative cache here
    public Result<BalanceSnapshot> getByRefId(String refId) {
        Result<BalanceSnapshot> snapshotResult = doGetByRefId(refId);
        if (snapshotResult.isSuccess()) {
            if (snapshotResult.getValue() == null) {
                // TODO should not set negative if already hit a negative (this could infinitely renewal the negative ttl)
                // set negative cache
                Result<Boolean> result = balanceSnapshotRefCache.afterDbMiss(refId);
                if (!result.isSuccess()) {
                    log.error("set negative result failed, ref_id: {}, result: {}", refId, result);
                }
            } else {
                // try to promote hot snapshot
                BalanceSnapshot balanceSnapshot = snapshotResult.getValue();
                promoteHotSnapshot(balanceSnapshot.getWalletId(), balanceSnapshot.getOwnerType());
            }

        }
        return snapshotResult;
    }

    private Result<BalanceSnapshot> doGetByRefId(String refId) {
        Result<CacheValueInfo<String>> refCacheResult = balanceSnapshotRefCache.get(refId);
        if (!refCacheResult.isSuccess()) {
            log.error("get cache failed, ref_id: {}, result: {}", refId, refCacheResult);
        } else {
            CacheValueInfo<String> cacheInfo = refCacheResult.getValue();
            if (CacheValueInfo.ifCacheHit(cacheInfo)) {
                String walletId = cacheInfo.value;
                if (cacheInfo.cacheType == CacheType.NEGATIVE) {
                    return Result.success(null);
                }
                return getByWalletId(walletId);
            }
        }

        BalanceSnapshot snapshot = balanceSnapshotRepository.getByWalletReferenceId(refId);
        if (snapshot != null) {
            Result<Boolean> setRefCacheResult = balanceSnapshotRefCache.afterDbHit(refId, snapshot.getWalletId());
            if (!setRefCacheResult.isSuccess()) {
                log.error("set ref cache failed, ref_id: {}, result: {}", refId, setRefCacheResult);
            }

            Result<Boolean> result = determineCacheStrategy(snapshot.getWalletId()).afterDbHit(snapshot.getWalletId(), snapshot, snapshot.getVersion());
            if (!result.isSuccess()) {
                log.error("doGetByRefId|afterDbHit cache failed, snapshot: {}, result: {}", snapshot, result);
            }
        }
        return Result.success(snapshot);
    }

    private void promoteHotSnapshot(String id, OwnerType ownerType) {
        if (ownerType == OwnerType.SYSTEM) {
            promotionClassifier.promote(id);
        }
    }

    private VersionCacheStrategy<BalanceSnapshot> determineCacheStrategy(String walletId) {
        return promotionClassifier.isPromoted(walletId) ? hotSnapshotCache : normalSnapshotCache;
    }
}
