package com.exchange.app.wallet.processor.balance;

import com.exchange.app.wallet.dao.store.BalanceSnapshotStore;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class GetBalanceSnapshotProcessorUnitTest {
    @Test
    public void getBalanceSnapshotShouldLoadByWalletId() {
        BalanceSnapshotStore balanceSnapshotStore = mock(BalanceSnapshotStore.class);
        GetBalanceSnapshotProcessor processor = new GetBalanceSnapshotProcessor(balanceSnapshotStore);
        BalanceSnapshot snapshot = BalanceSnapshot.create("wallet-1", ServiceId.USER, "ref-1", "asset-1", WalletStatus.OPEN, OwnerType.USER, "owner-1", 100L, 20L, 0L);

        when(balanceSnapshotStore.getByWalletId("wallet-1")).thenReturn(Result.success(snapshot));

        Result<BalanceSnapshot> result = processor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, "wallet-1");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(snapshot, result.getValue());
        verify(balanceSnapshotStore).getByWalletId("wallet-1");
    }

    @Test
    public void getBalanceSnapshotShouldReturnNotFoundWhenSnapshotMissing() {
        BalanceSnapshotStore balanceSnapshotStore = mock(BalanceSnapshotStore.class);
        GetBalanceSnapshotProcessor processor = new GetBalanceSnapshotProcessor(balanceSnapshotStore);

        when(balanceSnapshotStore.getByRefId(ServiceId.USER, "ref-miss")).thenReturn(Result.success(null));

        Result<BalanceSnapshot> result = processor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.REF_ID, ServiceId.USER, "ref-miss");

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(WalletServiceErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, result.getErrorCode());
        verify(balanceSnapshotStore).getByRefId(ServiceId.USER, "ref-miss");
    }

    @Test
    public void getBalanceSnapshotShouldRejectUnknownServiceIdForRefLookup() {
        BalanceSnapshotStore balanceSnapshotStore = mock(BalanceSnapshotStore.class);
        GetBalanceSnapshotProcessor processor = new GetBalanceSnapshotProcessor(balanceSnapshotStore);

        Result<BalanceSnapshot> result = processor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.REF_ID, ServiceId.UNKNOWN, "ref-1");

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(WalletServiceErrorCode.INVALID_REQUEST_PARAMETER, result.getErrorCode());
        verifyNoInteractions(balanceSnapshotStore);
    }
}
