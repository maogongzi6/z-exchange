//package com.exchange.app.wallet.dao.store;
//
//import com.exchange.app.wallet.constant.cache.CacheTtlStrategies;
//import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
//import com.exchange.app.wallet.po.enums.OwnerType;
//import com.exchange.app.wallet.po.enums.ServiceId;
//import com.exchange.app.wallet.po.enums.WalletStatus;
//import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
//import com.exchange.common.redis.cache.constant.CacheType;
//import com.exchange.common.redis.cache.model.CacheReadResult;
//import com.exchange.common.redis.cache.strategy.StableCacheStrategy;
//import com.exchange.common.utils.TtlStrategy;
//import org.junit.Assert;
//import org.junit.Test;
//
//import java.time.Duration;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
//public class BalanceSnapshotStoreUnitTest {
//    @Test
//    public void getByRefIdShouldLoadSnapshotFromWalletIdCacheHit() {
//        BalanceSnapshotRepository repository = mock(BalanceSnapshotRepository.class);
//        StableCacheStrategy<String> balanceSnapshotRefCache = mock(StableCacheStrategy.class);
//        CacheTtlStrategies cacheTtlStrategies = mock(CacheTtlStrategies.class);
//        BalanceSnapshotStore store = new BalanceSnapshotStore(repository, balanceSnapshotRefCache, cacheTtlStrategies);
//
//        BalanceSnapshot snapshot = BalanceSnapshot.create("wallet-1", ServiceId.USER, "ref-1", "asset-1", WalletStatus.OPEN, OwnerType.USER, "owner-1", 100L, 20L);
//        when(balanceSnapshotRefCache.get("ref-1"))
//                .thenReturn(com.exchange.common.result.Result.success(CacheReadResult.valueHit("wallet-1")));
//        when(repository.getByWalletId("wallet-1")).thenReturn(snapshot);
//
//        com.exchange.common.result.Result<BalanceSnapshot> result = store.getByRefId("ref-1");
//
//        Assert.assertTrue(result.isSuccess());
//        Assert.assertEquals(snapshot, result.getValue());
//        verify(repository).getByWalletId("wallet-1");
//        verify(repository, never()).getByWalletReferenceId(anyString());
//        verify(balanceSnapshotRefCache, never()).setNegative(anyString(), any());
//    }
//
//    @Test
//    public void getByRefIdShouldSetNegativeCacheOnDbMiss() {
//        BalanceSnapshotRepository repository = mock(BalanceSnapshotRepository.class);
//        StableCacheStrategy<String> balanceSnapshotRefCache = mock(StableCacheStrategy.class);
//        CacheTtlStrategies cacheTtlStrategies = mock(CacheTtlStrategies.class);
//        BalanceSnapshotStore store = new BalanceSnapshotStore(repository, balanceSnapshotRefCache, cacheTtlStrategies);
//        TtlStrategy negativeTtl = new TtlStrategy(Duration.ofSeconds(30), Duration.ZERO);
//
//        when(balanceSnapshotRefCache.get("ref-miss")).thenReturn(com.exchange.common.result.Result.success());
//        when(repository.getByWalletReferenceId("ref-miss")).thenReturn(null);
//        when(cacheTtlStrategies.getNegativeStrategy()).thenReturn(negativeTtl);
//        when(balanceSnapshotRefCache.setNegative("ref-miss", negativeTtl)).thenReturn(com.exchange.common.result.Result.success());
//
//        com.exchange.common.result.Result<BalanceSnapshot> result = store.getByRefId("ref-miss");
//
//        Assert.assertTrue(result.isSuccess());
//        Assert.assertNull(result.getValue());
//        verify(repository).getByWalletReferenceId("ref-miss");
//        verify(balanceSnapshotRefCache).setNegative("ref-miss", negativeTtl);
//    }
//}
