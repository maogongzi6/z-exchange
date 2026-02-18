package com.exchange.app.wallet.processor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.transaction.AtomicTransactionProcessor;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.*;
import com.exchange.proto.wallet.wallet.*;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.utility.RandomString;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class AtomicTransactionProcessorTest {
    @Autowired
    private CreateWalletProcessor createWalletProcessor;
    @Autowired
    private AtomicTransactionProcessor atomicTransactionProcessor;
    @Autowired
    private BalanceSnapshotMapper snapshotMapper;

    String usdOutWalletRef = "usd-out-wallet", usdInWalletRef = "usd-in-wallet";
    String cnyOutWalletRef = "cny-out-wallet", cnyInWalletRef = "cny-in-wallet";
    String usdAssetId = "asset-1", cnyAssetId = "asset-2";
    String owner = "sun";
    OwnerTypePb ownerType = OwnerTypePb.OwnerTypePb_User;
    ServiceIdPb serviceId = ServiceIdPb.ServiceIdPb_User;
    BusinessTypePb businessType = BusinessTypePb.BusinessTypePb_Transfer;
    @Test
    public void testAtomicSuccess() {
        createWallet();

        //String ref = "test-atomic-success-" + RandomString.make();
        String ref = "test-atomic-success-1" + "1";
        List<TransactionLinePb> lines = new ArrayList<>() {{
            add(TransactionLinePb.newBuilder().setWalletRef(usdOutWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Debit).setAmount(10).build());
            add(TransactionLinePb.newBuilder().setWalletRef(usdInWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Credit).setAmount(10).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyOutWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Debit).setAmount(40).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyInWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Credit).setAmount(40).build());

        }};
        AtomicTransactionRequestPb requestPb = AtomicTransactionRequestPb.newBuilder()
                .setReferenceId(ref).setInitiator(serviceId)
                .setIdempotencyKey(ref).setBusinessType(businessType).addAllLines(lines).build();
        AtomicTransactionReplyPb replyPb = atomicTransactionProcessor.executeAtomic(requestPb);
        log.info(replyPb.toString());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, replyPb.getError().getCode());

    }

    private void createWallet() {
        CreateWalletReplyPb reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdOutWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdInWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyOutWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyInWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());

        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, usdOutWalletRef).set(BalanceSnapshot::getAvailable, 100);
        snapshotMapper.update(wrapper);
        wrapper.clear();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, cnyOutWalletRef).set(BalanceSnapshot::getAvailable, 100);
        snapshotMapper.update(wrapper);
    }
}
