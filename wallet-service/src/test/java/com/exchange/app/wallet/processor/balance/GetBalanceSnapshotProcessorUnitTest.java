package com.exchange.app.wallet.processor.balance;

import com.exchange.app.wallet.dao.store.BalanceSnapshotStore;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Results;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetBalanceSnapshotProcessorUnitTest {
    @Test
    public void getBalanceSnapshotShouldLoadByWalletId() {
        BalanceSnapshotStore balanceSnapshotStore = mock(BalanceSnapshotStore.class);
        GetBalanceSnapshotProcessor processor = new GetBalanceSnapshotProcessor(balanceSnapshotStore);
        BalanceSnapshot snapshot = BalanceSnapshot.create("wallet-1", ServiceId.USER, "ref-1", "asset-1", WalletStatus.OPEN, OwnerType.USER, "owner-1", 100L, 20L);

        when(balanceSnapshotStore.getByWalletId("wallet-1")).thenReturn(Results.success(snapshot));

        com.exchange.common.utils.result.Result<BalanceSnapshot> result = processor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, "wallet-1");

        Assert.assertTrue(result.success());
        Assert.assertEquals(snapshot, result.value());
        verify(balanceSnapshotStore).getByWalletId("wallet-1");
    }

    @Test
    public void getBalanceSnapshotShouldReturnNotFoundWhenSnapshotMissing() {
        BalanceSnapshotStore balanceSnapshotStore = mock(BalanceSnapshotStore.class);
        GetBalanceSnapshotProcessor processor = new GetBalanceSnapshotProcessor(balanceSnapshotStore);

        when(balanceSnapshotStore.getByRefId("ref-miss")).thenReturn(Results.success(null));

        com.exchange.common.utils.result.Result<BalanceSnapshot> result = processor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.REF_ID, "ref-miss");

        Assert.assertFalse(result.success());
        Assert.assertEquals(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, result.errorCode());
        verify(balanceSnapshotStore).getByRefId("ref-miss");
    }
}
