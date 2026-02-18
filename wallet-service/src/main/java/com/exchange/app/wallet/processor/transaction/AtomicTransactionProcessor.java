package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.*;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AtomicTransactionProcessor {
    private final WalletTransactionRepository walletTransactionManager;

    private final TransactionProcessor transactionProcessor;

    public AtomicTransactionReplyPb executeAtomic(AtomicTransactionRequestPb request) {
        TransactionProcessor.RequestInfo requestInfo = new TransactionProcessor.RequestInfo(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.ATOMIC, request.getLinesList(), AtomicTransactionRequestPb.getDescriptor().getName());
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

    private AtomicTransactionReplyPb replySuccess(String txnId, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
        return AtomicTransactionReplyPb.newBuilder()
                .setTransactionId(txnId)
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private AtomicTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
        return AtomicTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumPbMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private AtomicTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        AtomicTransactionReplyPb.Builder builder = AtomicTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private AtomicTransactionReplyPb replyError(Result<?> result) {
        return replyError(Results.getErrorCode(result), result.errorDetail);
    }
}
