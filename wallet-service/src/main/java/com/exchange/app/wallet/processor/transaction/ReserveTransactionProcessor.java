package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.EnumMappers;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReserveTransactionProcessor {
    final private TransactionProcessor transactionProcessor;
    private final WalletReservationManager walletReservationManager;
    private final TransactionTemplate transactionTemplate;
    private final BalanceSnapshotManager balanceSnapshotManager;
    private final WalletTransactionManager walletTransactionManager;
    private final WalletActionManager walletActionManager;
    private final WalletOutboxManager walletOutboxManager;

    public ReserveTransactionReplyPb reserve(ReserveTransactionRequestPb request) {
        Result<TransactionProcessor.RequestInfo> result = validateAndGetRequestInfo(request);
        if (!result.success) {
            return replyError(result);
        }
        TransactionProcessor.RequestInfo requestInfo = result.value;
        WalletTransaction walletTxn = walletTransactionManager.selectByIdempotencyKey(requestInfo.serviceId, request.getIdempotencyKey());
        // idempotency check
        if (walletTxn != null) {
            Result<TransactionProcessor.TransactionInfo> txnInfoResult = transactionProcessor.getTxnInfo(walletTxn, requestInfo);
            if (!txnInfoResult.success) {
                return replyError(txnInfoResult);
            }
            return replySuccess(txnInfoResult.value, "SUCCESS, ALREADY RESERVED");
        }
        Result<TransactionProcessor.TransactionInfo> txnInfoResult = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getReferenceId(), requestInfo);
        if (!txnInfoResult.success) {
            return replyError(txnInfoResult);
        }

        return replySuccess(txnInfoResult.value, "SUCCESS");
    }

    private Result<TransactionProcessor.RequestInfo> validateAndGetRequestInfo(ReserveTransactionRequestPb request) {
        Result<TransactionProcessor.RequestInfo> result = transactionProcessor.validateAndGetRequestInfo(request.getInitiator(), request.getBusinessType(), TransactionType.TWO_STEP, false, request.getLinesList());
        if (!result.success) {
            return result;
        }
//        if (result.value.operationToSnapshots.get(OperationTypePb.OperationType_Reserve).size() != request.getLinesList().size()) {
//            throw new InvalidValueException(String.format("unexpected, building snapshot info failed, snapshots: %s, lines: %s", result.value.operationToSnapshots.get(OperationTypePb.OperationType_Reserve), request.getLinesList()));
//        }
        for (TransactionLinePb line : request.getLinesList()) {
            if (line.getOperationType() != OperationTypePb.OperationTypePb_Reserve) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line.getOperationType());
            }
        }
        return Result.success(result.value);
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

    private Result<List<WalletReservation>> getAndValidateReservation(WalletTransaction txn, List<TransactionProcessor.RequestInfo.Line> lines) {
        List<WalletReservation> reservations = walletReservationManager.selectByTxnId(txn.getTxnId());
        if (reservations.size() != lines.size()) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("invalid reservation size, reservations: %s, lines: %s", reservations, lines));
        }
        Map<String, WalletReservation> walletIdToReservation = reservations.stream().collect(Collectors.toMap(WalletReservation::getWalletId, reservation->reservation));
        for (TransactionProcessor.RequestInfo.Line line : lines) {
            WalletReservation reservation = walletIdToReservation.get(line.walletId);
            if (reservation == null || !Objects.equals(line.walletRef, reservation.getWalletReferenceId())
                    || !Objects.equals(line.assetCode, reservation.getAssetId())
                    || !Objects.equals(line.amount, reservation.getTotal())) {
                return Result.fail(ErrorCode.WALLET_RESERVATION_MISMATCH, String.format("invalid reservation: %s, line: %s", reservation, line));
            }
        }
        return Result.success(reservations);
    }

    private ReserveTransactionReplyPb replySuccess(TransactionProcessor.TransactionInfo info, String detail) {
        WalletTransaction txn = info.walletTxn;
        ReserveTransactionReplyPb.Builder builder = ReserveTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
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
        result = Result.requireNotNull(result);
        return replyError(result.errorCode, result.errorDetail);
    }

    @AllArgsConstructor
    static private class ReserveResult {
        final WalletTransaction walletTransaction;
        final List<WalletReservation> reservations;
    }
}
