package com.exchange.app.wallet.dao.store;

import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
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
    private final BalanceSnapshotRepository balanceRepository;
    private final RawCacheStrategy<String> hotBalanceRefCache;
    private final RawCacheStrategy<String> balanceRefCache;
    private final VersionCacheStrategy<BalanceSnapshot> hotBalanceCache;
    private final VersionCacheStrategy<BalanceSnapshot> normalBalanceCache;
    private final PromotionClassifier hotBalanceRefClassifier;
    private final PromotionClassifier hotBalanceClassifier;

    public BalanceSnapshotStore(
            BalanceSnapshotRepository balanceRepository,
            @Qualifier("hotBalanceRefCache") RawCacheStrategy<String> hotBalanceRefCache,
            @Qualifier("balanceRefCache") RawCacheStrategy<String> balanceRefCache,
            @Qualifier("hotBalanceCache") VersionCacheStrategy<BalanceSnapshot> hotBalanceCache,
            @Qualifier("normalBalanceCache") VersionCacheStrategy<BalanceSnapshot> normalBalanceCache,
            @Qualifier("hotBalanceClassifier") PromotionClassifier hotBalanceClassifier,
            @Qualifier("hotBalanceRefClassifier") PromotionClassifier hotBalanceRefClassifier) {
        this.balanceRepository = balanceRepository;
        this.hotBalanceRefCache = hotBalanceRefCache;
        this.balanceRefCache = balanceRefCache;
        this.hotBalanceCache = hotBalanceCache;
        this.normalBalanceCache = normalBalanceCache;
        this.hotBalanceClassifier = hotBalanceClassifier;
        this.hotBalanceRefClassifier = hotBalanceRefClassifier;
    }

    public void postInsert(BalanceSnapshot balanceSnapshot) {
        postInsert(balanceSnapshot.getWalletId(), balanceSnapshot.getServiceId(), balanceSnapshot.getWalletReferenceId(), balanceSnapshot.getOwnerType());

        Result<Boolean> result = determineBalanceStrategy(balanceSnapshot.getWalletId()).afterInsert(balanceSnapshot.getWalletId(), balanceSnapshot, balanceSnapshot.getVersion());
        if (!result.isSuccess()) {
            log.error("after_insert, cache strategy failed: {}, snapshot: {}", result, balanceSnapshot);
        }
    }

    public void postInsert(String walletId, ServiceId serviceId, String refId, OwnerType ownerType) {
        String scopedRefId = buildRefCacheId(serviceId, refId);
        promoteHotSnapshot(walletId, scopedRefId, ownerType);

        Result<Boolean> refResult = determineRefStrategy(scopedRefId).afterInsert(scopedRefId, walletId);
        if (!refResult.isSuccess()) {
            log.error("clean negative cache failed, serviceId: {}, ref_id: {}, refResult: {}", serviceId, refId, refResult);
        }
    }

    public void postDbUpdate(BalanceSnapshot balanceSnapshot) {
        Result<Boolean> result = determineBalanceStrategy(balanceSnapshot.getWalletId())
                .afterUpdate(balanceSnapshot.getWalletId(), balanceSnapshot, balanceSnapshot.getVersion());
        if (!result.isSuccess()) {
            log.error("after_update, cache strategy failed: {}, snapshot: {}", result, balanceSnapshot);
        }
    }

    public Result<BalanceSnapshot> getByWalletId(String walletId) {
        var result = doGetByWalletId(walletId);
        if (result.isSuccess() && result.getValue() != null) {
            BalanceSnapshot balanceSnapshot = result.getValue();
            String scopedRefId = buildRefCacheId(balanceSnapshot.getServiceId(), balanceSnapshot.getWalletReferenceId());
            promoteHotSnapshot(balanceSnapshot.getWalletId(), scopedRefId, balanceSnapshot.getOwnerType());
        }
        return result;
    }

    public Result<BalanceSnapshot> doGetByWalletId(String walletId) {
        var snapshotCache = determineBalanceStrategy(walletId);

        Result<CacheValueInfo<BalanceSnapshot>> cacheResult = snapshotCache.get(walletId);
        if (!cacheResult.isSuccess()) {
            log.error("getByWalletId cache failed, walletId: {}, result: {}", walletId, cacheResult);
        } else {
            CacheValueInfo<BalanceSnapshot> valueInfo = cacheResult.getValue();
            if (CacheValueInfo.ifCacheHit(valueInfo)) {
                return Result.success(valueInfo.value);
            }
        }

        BalanceSnapshot balanceSnapshot = balanceRepository.getByWalletId(walletId);
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
    public Result<BalanceSnapshot> getByRefId(ServiceId serviceId, String refId) {
        String scopedRefId = buildRefCacheId(serviceId, refId);
        Result<BalanceSnapshot> snapshotResult = doGetByRefId(serviceId, refId, scopedRefId);
        if (snapshotResult.isSuccess()) {
            if (snapshotResult.getValue() == null) {
                // TODO should not set negative if already hit a negative (this could infinitely renewal the negative ttl)
                // set negative cache
                Result<Boolean> result = determineRefStrategy(scopedRefId).afterDbMiss(scopedRefId);
                if (!result.isSuccess()) {
                    log.error("set negative result failed, serviceId: {}, ref_id: {}, result: {}", serviceId, refId, result);
                }
            } else {
                // try to promote hot snapshot
                BalanceSnapshot balanceSnapshot = snapshotResult.getValue();
                String scopedSnapshotRefId = buildRefCacheId(balanceSnapshot.getServiceId(), balanceSnapshot.getWalletReferenceId());
                promoteHotSnapshot(balanceSnapshot.getWalletId(), scopedSnapshotRefId, balanceSnapshot.getOwnerType());
            }

        }
        return snapshotResult;
    }

    private Result<BalanceSnapshot> doGetByRefId(ServiceId serviceId, String refId, String scopedRefId) {
        Result<CacheValueInfo<String>> refCacheResult = determineRefStrategy(scopedRefId).get(scopedRefId);
        if (!refCacheResult.isSuccess()) {
            log.error("get cache failed, serviceId: {}, ref_id: {}, result: {}", serviceId, refId, refCacheResult);
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

        BalanceSnapshot snapshot = balanceRepository.getByWalletReferenceId(serviceId, refId);
        if (snapshot != null) {
            Result<Boolean> setRefCacheResult = determineRefStrategy(scopedRefId).afterDbHit(scopedRefId, snapshot.getWalletId());
            if (!setRefCacheResult.isSuccess()) {
                log.error("set ref cache failed, serviceId: {}, ref_id: {}, result: {}", serviceId, refId, setRefCacheResult);
            }

            Result<Boolean> result = determineBalanceStrategy(snapshot.getWalletId()).afterDbHit(snapshot.getWalletId(), snapshot, snapshot.getVersion());
            if (!result.isSuccess()) {
                log.error("doGetByRefId|afterDbHit cache failed, snapshot: {}, result: {}", snapshot, result);
            }
        }
        return Result.success(snapshot);
    }

    private void promoteHotSnapshot(String walletId, String scopedRefId, OwnerType ownerType) {
        if (ownerType == OwnerType.SYSTEM) {
            hotBalanceClassifier.promote(walletId);
            hotBalanceRefClassifier.promote(scopedRefId);
        }
    }

    private RawCacheStrategy<String> determineRefStrategy(String scopedRefId) {
        return hotBalanceRefClassifier.isPromoted(scopedRefId) ? hotBalanceRefCache : balanceRefCache;
    }

    private VersionCacheStrategy<BalanceSnapshot> determineBalanceStrategy(String walletId) {
        return hotBalanceClassifier.isPromoted(walletId) ? hotBalanceCache : normalBalanceCache;
    }

    private String buildRefCacheId(ServiceId serviceId, String refId) {
        return serviceId.code + ":" + refId;
    }
}
