package com.exchange.app.wallet.processor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.transaction.ReserveTransactionProcessor;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.common.OwnerTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.wallet.*;
import lombok.extern.slf4j.Slf4j;
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
public class ReserveTransactionProcessorTest {
    String usdReserveWalletRef = "usd-reserve-wallet";
    String cnyReserveWalletRef = "cny-reserve-wallet";
    String usdAssetId = "asset-1", cnyAssetId = "asset-2";
    String owner = "sun";
    OwnerTypePb ownerType = OwnerTypePb.OwnerTypePb_User;
    ServiceIdPb serviceId = ServiceIdPb.ServiceIdPb_User;
    BusinessTypePb businessType = BusinessTypePb.BusinessTypePb_Transfer;
    @Autowired
    private CreateWalletProcessor createWalletProcessor;
    @Autowired
    private BalanceSnapshotMapper balanceSnapshotMapper;
    @Autowired
    private ReserveTransactionProcessor reserveTransactionProcessor;
    @Autowired
    private BalanceSnapshotRepository balanceSnapshotManager;

    @Test
    public void testReserveTransactionProcessor() {
        createWallet();

        String ref = "test-reserve-success-4";

        List<TransactionLinePb> lines = new ArrayList<>() {{
            add(TransactionLinePb.newBuilder().setWalletRef(usdReserveWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Reserve).setAmount(20).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyReserveWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Reserve).setAmount(40).build());

        }};
        ReserveTransactionRequestPb requestPb = ReserveTransactionRequestPb.newBuilder()
                .setReferenceId(ref).setInitiator(serviceId)
                .setIdempotencyKey(ref).setBusinessType(businessType).addAllLines(lines).build();
        ReserveTransactionReplyPb replyPb = reserveTransactionProcessor.reserve(requestPb);
        log.info(replyPb.toString());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, replyPb.getError().getCode());

    }

    private void createWallet() {
        CreateWalletReplyPb reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdReserveWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyReserveWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        List<BalanceSnapshot> snapshots = balanceSnapshotManager.selectByRefs(ServiceId.USER, List.of(usdReserveWalletRef));
        if (!snapshots.isEmpty() && snapshots.get(0).getAvailable() > 0) {
            return;
        }
        LambdaUpdateWrapper<BalanceSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, usdReserveWalletRef).set(BalanceSnapshot::getAvailable, 100);
        balanceSnapshotMapper.update(wrapper);
        wrapper.clear();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, cnyReserveWalletRef).set(BalanceSnapshot::getAvailable, 100);
        balanceSnapshotMapper.update(wrapper);
    }
}
