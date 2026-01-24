package com.exchange.app.wallet.processor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.mapper.WalletTransactionMapper;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.common.db.handler.AutofillMetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
//@ComponentScan(basePackages = {"com.exchange.app.wallet"}, basePackageClasses = {AutofillMetaObjectHandler.class})
public class SimpleTest {
    @Autowired
    WalletTransactionMapper walletTransactionMapper;
    @Autowired
    AutofillMetaObjectHandler autofillMetaObjectHandler;
    @Test
    public void test() throws InterruptedException {
        String txnId = "simple-test-4";
        WalletTransaction walletTransaction = WalletTransaction.create(txnId, "s-t", ServiceId.SYSTEM, txnId, TransactionStatus.CLOSED, TransactionType.ADJUST, BusinessType.TRANSFER);
        walletTransactionMapper.insert(walletTransaction);
        Thread.sleep(100);
        walletTransactionMapper.update(walletTransaction, new LambdaUpdateWrapper<>() {{set(WalletTransaction::getReferenceId, "changed"); }});
        WalletTransaction txn = walletTransactionMapper.selectOne(new LambdaUpdateWrapper<>() {{eq(WalletTransaction::getTxnId, txnId); }});
        System.out.println(txn);
    }
}
