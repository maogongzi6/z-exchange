package com.exchange.app.wallet.processor;

import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.balance.GetBalanceSnapshotProcessor;
import lombok.extern.slf4j.Slf4j;
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



    @Test
    public void testQueryUserBalance() {

    }

    @Test
    public void testQuerySystemBalance() {}

    private BalanceSnapshot createBalanceSnapshot(String walletId, OwnerType ownerType) {
        return BalanceSnapshot.create(walletId, ServiceId.SYSTEM, walletId, "asset-id", WalletStatus.OPEN, ownerType, "ower_id", 0L, 0L, 0L);

    }
}
