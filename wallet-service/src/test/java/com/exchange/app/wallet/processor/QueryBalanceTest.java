package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.balance.GetBalanceSnapshotProcessor;
import com.exchange.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.utility.RandomString;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class QueryBalanceTest {
    @Autowired
    GetBalanceSnapshotProcessor getBalanceSnapshotProcessor;
    @Autowired
    BalanceSnapshotRepository repository;


    @Test
    public void testQueryUserBalanceById() {
        String id = "test-query-user-" + RandomString.make();
        var r = createBalanceSnapshot(id, OwnerType.USER);
        repository.insertIgnore(r);
        Result<BalanceSnapshot> balanceSnapshotResult = getBalanceSnapshotProcessor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, id);
        Assert.assertTrue(balanceSnapshotResult.isSuccess());
        Assert.assertNotNull(balanceSnapshotResult.getValue());
    }

    @Test
    public void testQuerySystemBalanceById() {
        String id = "test-query-system-" + RandomString.make();
        var r = createBalanceSnapshot(id, OwnerType.SYSTEM);
        repository.insertIgnore(r);
        Result<BalanceSnapshot> balanceSnapshotResult = getBalanceSnapshotProcessor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, id);
        Assert.assertTrue(balanceSnapshotResult.isSuccess());
        Assert.assertNotNull(balanceSnapshotResult.getValue());
        balanceSnapshotResult = getBalanceSnapshotProcessor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.WALLET_ID, id);
    }

    @Test
    public void testQuerySystemBalanceByRefId() {
        String id = "test-query-system-ref-" + RandomString.make();
        var r = createBalanceSnapshot(id, OwnerType.SYSTEM);
        repository.insertIgnore(r);
        Result<BalanceSnapshot> balanceSnapshotResult = getBalanceSnapshotProcessor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.REF_ID, ServiceId.SYSTEM, id);
        Assert.assertTrue(balanceSnapshotResult.isSuccess());
        Assert.assertNotNull(balanceSnapshotResult.getValue());
        balanceSnapshotResult = getBalanceSnapshotProcessor.getBalanceSnapshot(GetBalanceSnapshotProcessor.LookupType.REF_ID, ServiceId.SYSTEM, id);
    }

    private BalanceSnapshot createBalanceSnapshot(String walletId, OwnerType ownerType) {
        return BalanceSnapshot.create(walletId, ServiceId.SYSTEM, walletId, "asset-id", WalletStatus.OPEN, ownerType, "ower_id", 0L, 0L, 0L);

    }
}
