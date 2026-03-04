package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.common.constant.GlobalServiceId;
import com.exchange.common.utils.TokenHelper;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.wallet.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReserveTransactionProcessor {
    final private TransactionProcessor transactionProcessor;

    public ReserveTransactionReplyPb reserve(ReserveTransactionRequestPb request) {
        Result<Void> validateResult = validate(request);
        if (validateResult.isFailed()) {
            return replyError(validateResult);
        }

        String token = TokenHelper.generateToken(GlobalServiceId.LEDGER.name());
        TransactionProcessor.RequestInfo requestInfo = new TransactionProcessor.RequestInfo(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.TWO_STEP, request.getLinesList(), ReserveTransactionRequestPb.getDescriptor().getName(), token);
        Result<String> idempResult = transactionProcessor.idempCheckAndReqValidate(requestInfo);
        if (idempResult.isFailed()) {
            return replyError(idempResult);
        } else if (!Strings.isEmpty(idempResult.value)) {
            return replySuccess(idempResult.value, idempResult.errorDetail);
        }

        Result<TransactionProcessor.TransactionInfo> result = transactionProcessor.beforePostingLedger(requestInfo, false);
        if (result.isFailed()) {
            return replyError(result);
        }

        return replySuccess(result.value, result.errorDetail);
    }

    private Result<Void> validate(ReserveTransactionRequestPb request) {
        for (TransactionLinePb line : request.getLinesList()) {
            if (line.getOperationType() != OperationTypePb.OperationTypePb_Reserve) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line.getOperationType());
            }
        }
        return Results.success();
    }

    private ReserveTransactionReplyPb replySuccess(String txnId, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
        return ReserveTransactionReplyPb.newBuilder()
                .setTransactionId(txnId)
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail))
                .build();
    }

    private ReserveTransactionReplyPb replySuccess(TransactionProcessor.TransactionInfo info, String detail) {
        detail = Objects.requireNonNullElse(detail, "");
        WalletTransaction txn = info.walletTxn;
        ReserveTransactionReplyPb.Builder builder = ReserveTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumPbMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
                .setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail));
        List<ReservationInfoPb> infos = new ArrayList<>();
        for (WalletReservation reservation : info.reservations) {
            ReservationInfoPb infoPb = ReservationInfoPb.newBuilder()
                    .setReservationRef(reservation.getReferenceId())
                    .setWalletRef(reservation.getWalletReferenceId())
                    .setAssetCode(reservation.getAssetId())
                    .setAmount(reservation.getRemaining()).build();
            infos.add(infoPb);
        }
        return builder.addAllInfos(infos).build();
    }

    private ReserveTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        ReserveTransactionReplyPb.Builder builder = ReserveTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private ReserveTransactionReplyPb replyError(Result<?> result) {
        return replyError(Results.getErrorCode(result), result.errorDetail);
    }

    @AllArgsConstructor
    static private class ReserveResult {
        final WalletTransaction walletTransaction;
        final List<WalletReservation> reservations;
    }
}
