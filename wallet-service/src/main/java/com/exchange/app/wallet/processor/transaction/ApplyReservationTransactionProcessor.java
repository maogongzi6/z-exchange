package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.processor.transaction.model.RequestInfo;
import com.exchange.app.wallet.processor.transaction.model.TransactionInfo;
import com.exchange.app.wallet.processor.transaction.step.BeforePostLedgerProcessor;
import com.exchange.app.wallet.processor.transaction.step.IdempPrecheckProcessor;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.utils.TokenHelper;
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
    private final BeforePostLedgerProcessor beforePostLedgerProcessor;
    private final IdempPrecheckProcessor idempPrecheckProcessor;

    public ApplyReservationTransactionReplyPb apply(ApplyReservationTransactionRequestPb request) {
        String token = TokenHelper.generateToken(GlobalServiceId.LEDGER.name());
        RequestInfo requestInfo = new RequestInfo(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.TWO_STEP, request.getLinesList(), ApplyReservationTransactionRequestPb.getDescriptor().getName(), token);
        Result<String> idempResult = idempPrecheckProcessor.idempAndValidatePrecheck(requestInfo);
        if (idempResult.isFailed()) {
            return replyError(idempResult);
        } else if (!Strings.isEmpty(idempResult.value)) {
            return replySuccess(idempResult.value, idempResult.errorDetail);
        }

        Result<TransactionInfo> result = beforePostLedgerProcessor.beforePostingLedger(requestInfo, true);
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
