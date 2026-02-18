package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.repository.WalletTransactionRepository;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionReplyPb;
import com.exchange.proto.wallet.wallet.ApplyReservationTransactionRequestPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ApplyReservationTransactionProcessor {
    private final WalletTransactionRepository walletTransactionManager;
    private final TransactionProcessor transactionProcessor;

    public ApplyReservationTransactionReplyPb apply(ApplyReservationTransactionRequestPb request) {
        TransactionProcessor.RequestInfo requestInfo = new TransactionProcessor.RequestInfo(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.TWO_STEP, request.getLinesList(), ApplyReservationTransactionRequestPb.getDescriptor().getName());
        Result<String> idempResult = transactionProcessor.idempCheckAndReqValidate(requestInfo);
        if (idempResult.isFailed()) {
            return replyError(idempResult);
        } else if (!Strings.isEmpty(idempResult.value)) {
            return replySuccess(idempResult.value, idempResult.errorDetail);
        }

        Result<TransactionProcessor.TransactionInfo> result = transactionProcessor.beforePostingLedger(requestInfo, true);
        if (result.isFailed()) {
            return replyError(result);
        }
        return replySuccess(result.value.walletTxn, result.errorDetail);
    }

    private ApplyReservationTransactionReplyPb replySuccess(String txnId, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
        return ApplyReservationTransactionReplyPb.newBuilder().setTransactionId(txnId)
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private ApplyReservationTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
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
        return replyError(Results.getErrorCode(result), result.errorDetail);
    }
}
