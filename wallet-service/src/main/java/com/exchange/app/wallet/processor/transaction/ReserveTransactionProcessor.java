package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.repository.*;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.EnumPbMappers;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.wallet.common.OperationTypePb;
import com.exchange.proto.wallet.wallet.ReservationInfoPb;
import com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb;
import com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb;
import com.exchange.proto.wallet.wallet.TransactionLinePb;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReserveTransactionProcessor {
    final private TransactionProcessor transactionProcessor;
    private final WalletReservationRepository walletReservationManager;
    private final WalletTransactionRepository walletTransactionManager;

    public ReserveTransactionReplyPb reserve(ReserveTransactionRequestPb request) {
        Result<Void> validateResult = validate(request);
        if (validateResult.isFailed()) {
            replyError(validateResult);
        }

        Result<TransactionProcessor.TransactionInfo> result = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getInitiator(), request.getIdempotencyKey(), request.getBusinessType(), TransactionType.TWO_STEP, false, request.getLinesList());
        if (result.isFailed()) {
            return replyError(result);
        }

        return replySuccess(result.value, "SUCCESS");
    }

    private Result<Void> validate(ReserveTransactionRequestPb request) {
        for (TransactionLinePb line : request.getLinesList()) {
            if (line.getOperationType() != OperationTypePb.OperationTypePb_Reserve) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line.getOperationType());
            }
        }
        return Results.success();
    }

//    private Result<ReserveResult> createReserveTransaction(ReserveTransactionRequestPb request, TransactionProcessor.RequestInfo requestInfo) {
//        // TODO precheck local cached asset table for status
//
//        WalletTransaction walletTxn = transactionProcessor.createTransaction(TransactionType.TWO_STEP, request.getReferenceId(), request.getIdempotencyKey(), requestInfo);
//        // no need to post ledger, complete immediately
//        walletTxn.setTxnStatus(TransactionStatus.COMPLETED);
//
//        // create action and reservation
//        List<WalletAction> actions = new ArrayList<>();
//        List<WalletReservation> reservations = new ArrayList<>();
//        for (TransactionProcessor.RequestInfo.Line line : requestInfo.lines) {
//            reservations.add(transactionProcessor.createReservation(walletTxn, line));
//            actions.add(transactionProcessor.createReserveAction(walletTxn, line));
//        }
//
//        WalletOutbox walletOutbox = transactionProcessor.createOutbox(walletTxn, requestInfo);
//
//        Result<Void> result = transactionProcessor.beforePostingLedger(walletTxn, actions, reservations, walletOutbox, requestInfo);
//        if (!result.success) {
//            return Result.fail(result);
//        }
//        return Result.success(new ReserveResult(walletTxn, reservations));
//    }

//    private Result<List<WalletReservation>> getAndValidateReservation(WalletTransaction txn, List<TransactionProcessor.RequestInfo.Line> lines) {
//        List<WalletReservation> reservations = walletReservationManager.selectByTxnId(txn.getTxnId());
//        if (reservations.size() != lines.size()) {
//            return Results.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("invalid reservation size, reservations: %s, lines: %s", reservations, lines));
//        }
//        Map<String, WalletReservation> walletIdToReservation = reservations.stream().collect(Collectors.toMap(WalletReservation::getWalletId, reservation->reservation));
//        for (TransactionProcessor.RequestInfo.Line line : lines) {
//            WalletReservation reservation = walletIdToReservation.get(line.walletId);
//            if (reservation == null || !Objects.equals(line.walletRef, reservation.getWalletReferenceId())
//                    || !Objects.equals(line.assetCode, reservation.getAssetId())
//                    || !Objects.equals(line.amount, reservation.getTotal())) {
//                return Results.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("invalid reservation: %s, line: %s", reservation, line));
//            }
//        }
//        return Results.success(reservations);
//    }

    private ReserveTransactionReplyPb replySuccess(TransactionProcessor.TransactionInfo info, String detail) {
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
