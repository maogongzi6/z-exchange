package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.manager.WalletTransactionManager;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionReplyPb;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionRequestPb;
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
        Result<TransactionProcessor.TransactionInfo> result = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.TWO_STEP, true, request.getLinesList());
        if (!result.success) {
            return replyError(result);
        }
        return replySuccess(result.value.walletTxn, "success");
    }

    private ApplyReservationTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        return ApplyReservationTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumPbMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
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
