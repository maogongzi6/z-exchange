package com.exchange.app.wallet.processor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exchange.app.wallet.dao.repository.BalanceSnapshotRepository;
import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.processor.transaction.ApplyReservationTransactionProcessor;
import com.exchange.app.wallet.processor.transaction.ReserveTransactionProcessor;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.common.OwnerTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
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
import java.util.TreeMap;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class ApplyReservationTransactionProcessorTest {
    String usdReserveWalletRef = "usd-reserve-wallet";
    String cnyReserveWalletRef = "cny-reserve-wallet";
    String usdOutWalletRef = "usd-out-wallet", usdInWalletRef = "usd-in-wallet";
    String cnyOutWalletRef = "cny-out-wallet", cnyInWalletRef = "cny-in-wallet";
    String usdAssetId = "asset-1", cnyAssetId = "asset-2";
    String owner = "sun";
    OwnerTypePb ownerType = OwnerTypePb.OwnerTypePb_User;
    ServiceIdPb serviceId = ServiceIdPb.ServiceIdPb_User;
    BusinessTypePb businessType = BusinessTypePb.BusinessTypePb_Transfer;
    @Autowired
    private CreateWalletProcessor createWalletProcessor;
    @Autowired
    private BalanceSnapshotRepository balanceSnapshotManager;
    @Autowired
    private BalanceSnapshotMapper balanceSnapshotMapper;
    @Autowired
    private ApplyReservationTransactionProcessor applyReservationTransactionProcessor;
    @Autowired
    private ReserveTransactionProcessor reserveTransactionProcessor;

    @Test
    public void testApplyReservationTransactionProcessor() {
        createWallet();

        var replyPb = applyReservation();
        log.info(replyPb.toString());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, replyPb.getError().getCode());
    }

    @Test
    public void batchTestApplyReservationTransactionProcessor() {
        createWallet();

        int batchSize = 10;

        for (int i = 0; i < batchSize; i++) {
            var replyPb = applyReservation();
            log.info(replyPb.toString());
            Assert.assertEquals(ErrorCodePb.ERROR_OK, replyPb.getError().getCode());
        }
    }

    @Test
    public void testPureRelease() {
        createWallet();
        var replyPb = pureRelease();
        log.info(replyPb.toString());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, replyPb.getError().getCode());
    }

    private ApplyReservationTransactionReplyPb applyReservation() {
        var reserveReply = reserve();
        var infoList = reserveReply.getInfosList();
        String reserveCnyRef = "", reserveUsdRef = "";
        if (infoList.size() == 2) {
            for (ReservationInfoPb info : infoList) {
                if (info.getAssetCode().equals(cnyAssetId)) {
                    reserveCnyRef = info.getReservationRef();
                } else {
                    reserveUsdRef = info.getReservationRef();
                }
            }
        } else {
            throw new RuntimeException();
        }

         String ref = "test-apply-success-" + RandomString.make();
        //String ref = "test-apply-success-10";

        String finalReserveUsdRef = reserveUsdRef;
        String finalReserveCnyRef = reserveCnyRef;
        List<TransactionLinePb> lines = new ArrayList<>() {{
            add(TransactionLinePb.newBuilder().setWalletRef(usdReserveWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Earmark).setAmount(5).setReservationRef(finalReserveUsdRef).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyReserveWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Release).setAmount(20).setReservationRef(finalReserveCnyRef).build());
            add(TransactionLinePb.newBuilder().setWalletRef(usdOutWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Debit).setAmount(10).build());
            add(TransactionLinePb.newBuilder().setWalletRef(usdInWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Credit).setAmount(15).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyOutWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Debit).setAmount(10).build());
            add(TransactionLinePb.newBuilder().setWalletRef(cnyInWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Credit).setAmount(10).build());

        }};
        ApplyReservationTransactionRequestPb requestPb = ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId(ref).setInitiator(serviceId)
                .setIdempotencyKey(ref).setBusinessType(businessType).addAllLines(lines).build();
        return applyReservationTransactionProcessor.apply(requestPb);
    }

    private ApplyReservationTransactionReplyPb pureRelease() {
        var reserveReply = reserve();
        var infoList = reserveReply.getInfosList();
        String reserveCnyRef = "", reserveUsdRef = "";
        if (infoList.size() == 2) {
            for (ReservationInfoPb info : infoList) {
                if (info.getAssetCode().equals(cnyAssetId)) {
                    reserveCnyRef = info.getReservationRef();
                } else {
                    reserveUsdRef = info.getReservationRef();
                }
            }
        } else {
            throw new RuntimeException();
        }

        String ref = "test-release-success-" + RandomString.make();
        //String ref = "test-apply-success-10";

        String finalReserveUsdRef = reserveUsdRef;
        String finalReserveCnyRef = reserveCnyRef;
        List<TransactionLinePb> lines = new ArrayList<>() {{
            add(TransactionLinePb.newBuilder().setWalletRef(cnyReserveWalletRef).setAssetCode(cnyAssetId).setOperationType(OperationTypePb.OperationTypePb_Release).setAmount(20).setReservationRef(finalReserveCnyRef).build());
            add(TransactionLinePb.newBuilder().setWalletRef(usdReserveWalletRef).setAssetCode(usdAssetId).setOperationType(OperationTypePb.OperationTypePb_Release).setAmount(5).setReservationRef(finalReserveUsdRef).build());


        }};
        ApplyReservationTransactionRequestPb requestPb = ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId(ref).setInitiator(serviceId)
                .setIdempotencyKey(ref).setBusinessType(businessType).addAllLines(lines).build();
        return applyReservationTransactionProcessor.apply(requestPb);
    }

    private ReserveTransactionReplyPb reserve() {
        String ref = "test-reserve-success-" + RandomString.make();
        //String ref = "test-reserve-success-10";
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
        return replyPb;
    }

    private void createWallet() {
        CreateWalletReplyPb reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdReserveWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyReserveWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdOutWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(usdInWalletRef).setAssetId(usdAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyOutWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        reply = createWalletProcessor.createWallet(CreateWalletRequestPb.newBuilder().setServiceId(serviceId).setReferenceId(cnyInWalletRef).setAssetId(cnyAssetId).setOwnerType(ownerType).setOwnerId(owner).build());
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
        wrapper.clear();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, usdOutWalletRef).set(BalanceSnapshot::getAvailable, 100);
        balanceSnapshotMapper.update(wrapper);
        wrapper.clear();
        wrapper.eq(BalanceSnapshot::getWalletReferenceId, cnyOutWalletRef).set(BalanceSnapshot::getAvailable, 100);
        balanceSnapshotMapper.update(wrapper);
    }
}
