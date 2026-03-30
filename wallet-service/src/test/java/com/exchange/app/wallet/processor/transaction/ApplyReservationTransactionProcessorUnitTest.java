package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.processor.transaction.model.TransactionInfo;
import com.exchange.app.wallet.processor.transaction.step.BeforePostLedgerProcessor;
import com.exchange.app.wallet.processor.transaction.step.IdempPrecheckProcessor;
import com.exchange.app.wallet.result.Results;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.wallet.common.BusinessTypePb;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;
import com.exchange.proto.wallet.common.TransactionStatusPb;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionReplyPb;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionRequestPb;
import com.exchange.proto.wallet.wallet.TransactionLinePb;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplyReservationTransactionProcessorUnitTest {
    @Test
    public void applyShouldSkipLedgerPostingForReleaseOnlyRequest() {
        BeforePostLedgerProcessor beforePostLedgerProcessor = mock(BeforePostLedgerProcessor.class);
        IdempPrecheckProcessor idempPrecheckProcessor = mock(IdempPrecheckProcessor.class);
        ApplyReservationTransactionProcessor processor = new ApplyReservationTransactionProcessor(beforePostLedgerProcessor, idempPrecheckProcessor);

        when(idempPrecheckProcessor.idempAndValidatePrecheck(any())).thenReturn(Results.success());
        WalletTransaction txn = WalletTransaction.create("txn-release", "ref-release", ServiceId.USER, "idem-release", TransactionStatus.COMPLETED, TransactionType.TWO_STEP, BusinessType.TRANSFER);
        when(beforePostLedgerProcessor.beforePostingLedger(any(), eq(false))).thenReturn(Results.success(new TransactionInfo(txn, null, null, null, null)));

        ApplyReservationTransactionReplyPb reply = processor.apply(ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId("ref-release")
                .setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setIdempotencyKey("idem-release")
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .addLines(TransactionLinePb.newBuilder()
                        .setWalletRef("wallet-1")
                        .setAssetCode("asset-1")
                        .setReservationRef("reservation-1")
                        .setOperationType(OperationTypePb.OperationTypePb_Release)
                        .setAmount(10)
                        .build())
                .build());

        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        Assert.assertEquals(TransactionStatusPb.TransactionStatusPb_Completed, reply.getStatus());
        verify(beforePostLedgerProcessor).beforePostingLedger(any(), eq(false));
    }

    @Test
    public void applyShouldKeepPostingLedgerWhenTransferActionExists() {
        BeforePostLedgerProcessor beforePostLedgerProcessor = mock(BeforePostLedgerProcessor.class);
        IdempPrecheckProcessor idempPrecheckProcessor = mock(IdempPrecheckProcessor.class);
        ApplyReservationTransactionProcessor processor = new ApplyReservationTransactionProcessor(beforePostLedgerProcessor, idempPrecheckProcessor);

        when(idempPrecheckProcessor.idempAndValidatePrecheck(any())).thenReturn(Results.success());
        WalletTransaction txn = WalletTransaction.create("txn-transfer", "ref-transfer", ServiceId.USER, "idem-transfer", TransactionStatus.PENDING, TransactionType.TWO_STEP, BusinessType.TRANSFER);
        when(beforePostLedgerProcessor.beforePostingLedger(any(), eq(true))).thenReturn(Results.success(new TransactionInfo(txn, null, null, null, null)));

        ApplyReservationTransactionReplyPb reply = processor.apply(ApplyReservationTransactionRequestPb.newBuilder()
                .setReferenceId("ref-transfer")
                .setInitiator(ServiceIdPb.ServiceIdPb_User)
                .setIdempotencyKey("idem-transfer")
                .setBusinessType(BusinessTypePb.BusinessTypePb_Transfer)
                .addLines(TransactionLinePb.newBuilder()
                        .setWalletRef("wallet-1")
                        .setAssetCode("asset-1")
                        .setReservationRef("reservation-1")
                        .setOperationType(OperationTypePb.OperationTypePb_Release)
                        .setAmount(10)
                        .build())
                .addLines(TransactionLinePb.newBuilder()
                        .setWalletRef("wallet-2")
                        .setAssetCode("asset-1")
                        .setReservationRef("reservation-2")
                        .setOperationType(OperationTypePb.OperationTypePb_Earmark)
                        .setAmount(10)
                        .build())
                .build());

        Assert.assertEquals(ErrorCodePb.ERROR_OK, reply.getError().getCode());
        Assert.assertEquals(TransactionStatusPb.TransactionStatusPb_Pending, reply.getStatus());
        verify(beforePostLedgerProcessor).beforePostingLedger(any(), eq(true));
    }
}
