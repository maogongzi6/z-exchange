package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.manager.WalletTransactionManager;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.EnumMappers;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionReplyPb;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionRequestPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ApplyReservationTransactionProcessor {
    private final WalletTransactionManager walletTransactionManager;
    private final TransactionProcessor transactionProcessor;

    public ApplyReservationTransactionReplyPb apply(ApplyReservationTransactionRequestPb request) {
        Result<TransactionProcessor.RequestInfo> infoResult = transactionProcessor.validateAndGetRequestInfo(request.getInitiator(), request.getBusinessType(), TransactionType.TWO_STEP, true, request.getLinesList());
        if (!infoResult.success) {
            return replyError(infoResult);
        }
        TransactionProcessor.RequestInfo requestInfo = infoResult.value;
        WalletTransaction walletTxn = walletTransactionManager.selectByIdempotencyKey(requestInfo.serviceId, request.getIdempotencyKey());
        Result<TransactionProcessor.TransactionInfo> txnInfoResult;
        // idempotency check
        if (walletTxn == null) {
            txnInfoResult = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getIdempotencyKey(), requestInfo);
        } else if (walletTxn.getTxnStatus() == TransactionStatus.PENDING) {
            txnInfoResult = transactionProcessor.getTxnInfo(walletTxn, requestInfo);
        } else {
            return replySuccess(walletTxn, "txn already executed");
        }
        if (!txnInfoResult.success) {
            return replyError(txnInfoResult);
        }
        TransactionProcessor.TransactionInfo transactionInfo = txnInfoResult.value;
        Result<Void> result = transactionProcessor.postLedger(transactionInfo);
        if (!result.success) {
            return replyError(result);
        }
        Result<WalletTransaction> txnResult = transactionProcessor.afterPostingLedger(requestInfo, transactionInfo);
        if (!txnResult.success) {
            return replyError(txnResult);
        }

        return replySuccess(txnResult.value, "success");
    }

    private ApplyReservationTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        return ApplyReservationTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private ApplyReservationTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        ApplyReservationTransactionReplyPb.Builder builder = ApplyReservationTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private ApplyReservationTransactionReplyPb replyError(Result<?> result) {
        result = Result.requireNotNull(result);
        return replyError(result.errorCode, result.errorDetail);
    }
}
