package com.exchange.app.wallet.processor.transaction;

import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.*;
import com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AtomicTransactionProcessor {
    private final WalletTransactionManager walletTransactionManager;

    private final TransactionProcessor transactionProcessor;

    public AtomicTransactionReplyPb executeAtomic(AtomicTransactionRequestPb request) {
        // TODO check idempotency key and reference id
        Result<TransactionProcessor.RequestInfo> infoResult = transactionProcessor.validateAndGetRequestInfo(request.getInitiator(), request.getBusinessType(), TransactionType.ATOMIC, true, request.getLinesList());
        if (!infoResult.success) {
            return replyError(infoResult);
        }
        TransactionProcessor.RequestInfo requestInfo = infoResult.value;
        WalletTransaction walletTxn = walletTransactionManager.selectByIdempotencyKey(requestInfo.serviceId, request.getIdempotencyKey());
        Result<TransactionProcessor.TransactionInfo> txnInfoResult;
        // idempotency check
        if (walletTxn == null) {
            txnInfoResult = transactionProcessor.beforePostingLedger(request.getReferenceId(), request.getIdempotencyKey(), requestInfo);
        } else {
            return replySuccess(walletTxn, "txn already executed");
        }
//        } else if (walletTxn.getTxnStatus() == TransactionStatus.PENDING) {
//            txnInfoResult = transactionProcessor.getTxnInfo(walletTxn, requestInfo);
//        } else {
//            return replySuccess(walletTxn, "txn already executed");
//        }
        if (!txnInfoResult.success) {
            return replyError(txnInfoResult);
        }
//        TransactionProcessor.TransactionInfo transactionInfo = txnInfoResult.value;
//        Result<Void> result = transactionProcessor.postLedger(transactionInfo);
//        if (!result.success) {
//            return replyError(result);
//        }
//        Result<WalletTransaction> txnResult = transactionProcessor.afterPostingLedger(requestInfo, transactionInfo);
//        if (!txnResult.success) {
//            return replyError(txnResult);
//        }

        return replySuccess(txnInfoResult.value.walletTxn, "success");
    }

    /*
    private Result<RequestInfo> validateAndGetRequestInfo(AtomicTransactionRequestPb request) {
        ServiceId serviceId = EnumMappers.serviceIdPbMapper.to(request.getInitiator());
        BusinessType businessType = EnumMappers.businessTypePbMapper.to(request.getBusinessType());
        if (serviceId == null || serviceId == ServiceId.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid service id: " + request.getInitiator());
        }
        if (businessType == null || businessType == BusinessType.UNKNOWN) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid business type: " + request.getBusinessType());
        }

        Map<String, Long> inAssetToAmount = new HashMap<>(), outAssetToAmount = new HashMap<>();
        List<String> walletRefs = new ArrayList<>();
        for (TransactionLinePb line : request.getLinesList()) {
            // validate action type and amount
            if (line.getOperationType() != OperationTypePb.OperationTypePb_Debit && line.getOperationType() != OperationTypePb.OperationTypePb_Credit) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid operation type: " + line);
            }
            if (line.getAmount() <= 0) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid amount: " + line);
            }

            Map<String, Long> targetAssetToAmount = line.getOperationType() == OperationTypePb.OperationTypePb_Debit ? outAssetToAmount : inAssetToAmount;
            if (!targetAssetToAmount.containsKey(line.getAssetCode())) {
                targetAssetToAmount.put(line.getAssetCode(), 0L);
            }
            targetAssetToAmount.put(line.getAssetCode(), line.getAmount() + line.getAmount());

            // each wallet only have one line
            if (walletRefs.contains(line.getWalletRef())) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "duplicate wallet ref: " + line);
            }
            walletRefs.add(line.getWalletRef());
        }

        // TODO leave this when refactor
        if (inAssetToAmount.isEmpty() || outAssetToAmount.isEmpty()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "abnormal lines, transfer_in_size: " + inAssetToAmount.size() + ", transfer_out_size: " + outAssetToAmount.size());
        }
        if (inAssetToAmount.size() != outAssetToAmount.size()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "abnormal lines, asset types not match");
        }
        // validate: for each asset, transfer in should match transfer out
        for (Map.Entry<String, Long> entry : inAssetToAmount.entrySet()) {
            String inAssetCode = entry.getKey();
            Long inAmount = entry.getValue();
            if (!Objects.equals(inAmount, outAssetToAmount.get(inAssetCode))) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced asset: " + inAssetCode + ", in_amount: " + inAmount + ", out_amount" + outAssetToAmount.get(inAssetCode));
            }
        }

        // get id, wallet_id from wallet_ref (index cover)
        List<BalanceSnapshot> walletIdAndRefs = balanceSnapshotManager.selectIdByRefs(serviceId, walletRefs), transferInIdAndRefs = new ArrayList<>(), transferOutIdAndRefs = new ArrayList<>();
        Map<String, BalanceSnapshot> refToSnapshot = walletIdAndRefs.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletReferenceId, snapshot -> snapshot));
        List<RequestInfo.Line> lineInfos = new ArrayList<>();
        for (TransactionLinePb line : request.getLinesList()) {
            ActionType actionType = line.getOperationType() == OperationTypePb.OperationTypePb_Credit ? ActionType.TRANSFER_IN : ActionType.TRANSFER_OUT;
            BalanceSnapshot snapshot = refToSnapshot.get(line.getWalletRef());
            if (snapshot == null) {
                return Result.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "snapshot not found: " + line.getWalletRef());
            }
            if (actionType == ActionType.TRANSFER_IN) {
                transferInIdAndRefs.add(snapshot);
            } else {
                transferOutIdAndRefs.add(snapshot);
            }
            lineInfos.add(new RequestInfo.Line(snapshot.getWalletId(), line.getWalletRef(), line.getAssetCode(), actionType, line.getAmount()));
        }
        return Result.success(new RequestInfo(serviceId, businessType, walletIdAndRefs, transferInIdAndRefs, transferOutIdAndRefs, lineInfos));
    }

    private Result<TransactionInfo> createWalletTxn(AtomicTransactionRequestPb request, RequestInfo requestInfo) {
        // TODO precheck local cached asset table for status

        WalletTransaction walletTxn = createTransaction(request, requestInfo);

        // create action and reservation
        List<WalletAction> actions = new ArrayList<>();
        List<WalletReservation> reservations = new ArrayList<>();
        for (RequestInfo.Line line : requestInfo.lines) {
            if (line.actionType == ActionType.TRANSFER_OUT) {
                WalletReservation reservation = createReservation(walletTxn, line);
                reservations.add(reservation);
                actions.add(createReserveAction(walletTxn, line, reservation.getReservationId()));
                actions.add(createConsumeAction(walletTxn, line, reservation.getReservationId()));
            }
            actions.add(createTransferAction(walletTxn, line));
        }

        WalletOutbox walletOutbox = createOutbox(walletTxn, requestInfo);

        Result<Void> result = createWalletTxnDbTransaction(walletTxn, actions, reservations, walletOutbox, requestInfo);
        if (!result.success) {
            return Result.fail(result);
        }

        // fill id into reservations
        List<WalletReservation> reservationWithId = walletReservationManager.selectByTxnId(walletTxn.getTxnId(), new LambdaQueryWrapper<>() {{
            eq(WalletReservation::getReservationStatus, TransactionStatus.PENDING);
        }});
        if (reservationWithId.size() != reservations.size()) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch: " + reservations.size() + ", " + reservationWithId.size());
        }
        return Result.success(TransactionInfo.create(walletTxn, actions, reservationWithId, walletOutbox));
    }

    private Result<Void> createWalletTxnDbTransaction(WalletTransaction walletTxn, List<WalletAction> actions, List<WalletReservation> reservations, WalletOutbox walletOutbox, RequestInfo requestInfo) {
        // get reserve actions
        List<WalletAction> reserveActions = actions.stream().filter(action -> action.getActionType() == ActionType.RESERVE).collect(Collectors.toList());
        // map wallet id to reserve action
        Map<String, WalletAction> walletIdToReserveAction = reserveActions.stream().collect(Collectors.toMap(WalletAction::getWalletId, action -> action));

        List<BalanceSnapshot> transferOutInIdOrder = requestInfo.outSnapshotIdAndRefs.stream().sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());

        return DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {

            // update transfer-out snapshot to reserve money by id order
            for (BalanceSnapshot snapshot : transferOutInIdOrder) {
                WalletAction reserveAction = walletIdToReserveAction.get(snapshot.getWalletId());
                if (balanceSnapshotManager.reserveFromWalletId(snapshot.getId(), reserveAction.getWalletId(), reserveAction.getAssetId(), reserveAction.getAmount()) == 0) {
                    return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "failed to update reserve amount");
                }
            }

            if (walletReservationManager.batchInsert(reservations) != reservations.size()) {
                return Result.fail(ErrorCode.WALLET_RESERVATION_DUPLICATED, "unexpected reservation duplicated");
            }
            if (walletTransactionManager.insertIgnore(walletTxn) == 0) {
                return Result.fail(ErrorCode.WALLET_TRANSACTION_DUPLICATED, "unexpected transaction duplicated");
            }
            if (walletActionManager.batchInsert(actions) != actions.size()) {
                return Result.fail(ErrorCode.WALLET_ACTION_DUPLICATION, "unexpected action duplicated");
            }
            if (walletOutboxManager.insertIgnore(walletOutbox) == 0) {
                return Result.fail(ErrorCode.WALLET_OUTBOX_DUPLICATED, "unexpected outbox duplicated");
            }
            return Result.success();
        });
    }

    private Result<TransactionInfo> getTransactionInfo(AtomicTransactionRequestPb request, RequestInfo requestInfo, WalletTransaction txn) {
        List<WalletAction> requiredActions = walletActionManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
            in(WalletAction::getActionType, ActionType.TRANSFER_IN, ActionType.TRANSFER_OUT, ActionType.CONSUME);
        }});
        List<WalletReservation> reservations = walletReservationManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
            eq(WalletReservation::getReservationStatus, TransactionStatus.PENDING);
        }});
        WalletOutbox outbox = walletOutboxManager.selectByIdempotencyKey(txn.getReferenceId());

        TransactionInfo transactionInfo = TransactionInfo.create(txn, requiredActions, reservations, outbox);
        return Result.result(transactionInfo, validateTransferInfo(transactionInfo, request, requestInfo));
    }

    private Result<Void> validateTransferInfo(TransactionInfo transactionInfo, AtomicTransactionRequestPb req, RequestInfo requestInfo) {
        if (transactionInfo.walletTxn.getTxnStatus() == TransactionStatus.UNKNOWN || transactionInfo.walletTxn.getTxnType() != TransactionType.ATOMIC) {
            return Result.fail(ErrorCode.WALLET_TRANSACTION_INVALID, "invalid transaction: " + transactionInfo.walletTxn);
        }

        if (transactionInfo.walletIdToTransferAction.size() != req.getLinesList().size()) {
            log.error("transferActions: {}", transactionInfo.walletIdToTransferAction);
            return Result.fail(ErrorCode.WALLET_ACTION_NOT_FOUND, "unexpected transfer_action not found");
        }
        if (transactionInfo.reservations.size() != transactionInfo.walletIdToConsumeAction.size()) {
            // severe db state mismatch here
            log.error("reservation and consume mismatch, reservations: {}, wallet_id_to_consume_action: {}", transactionInfo, transactionInfo.walletIdToConsumeAction);
            return Result.fail(ErrorCode.DB_STATE_MISMATCH, "unexpected wallet_reservation mismatch");
        }

        Map<String, WalletReservation> walletIdToReservation = transactionInfo.reservations.stream().collect(Collectors.toMap(WalletReservation::getWalletId, reservation -> reservation));
        for (RequestInfo.Line line : requestInfo.lines) {
            WalletAction action = transactionInfo.walletIdToTransferAction.get(line.walletId);
            if (action == null) {
                log.error("action not found, walletId: {}", line.walletId);
                return Result.fail(ErrorCode.WALLET_ACTION_NOT_FOUND, "unexpected action not found");
            }
            // line should match action info
            if (line.actionType != action.getActionType()
                    || !Objects.equals(line.assetCode, action.getAssetId())
                    || !Objects.equals(line.amount, action.getAmount())
                    || !Objects.equals(line.walletId, action.getWalletId())) {
                log.error("action info not match, line: {}, action: {}", line, action);
                return Result.fail(ErrorCode.WALLET_ACTION_MISMATCH, "unexpected action info not match");
            }
            if (action.getActionType() == ActionType.TRANSFER_OUT) {
                // each transfer-out should have one corresponding consume
                WalletAction consumeAction = transactionInfo.walletIdToConsumeAction.get(line.walletId);
                if (!ActionHelper.isConsumeTransferOutPair(consumeAction, action)) {
                    // severe db state mismatch here
                    log.error("transfer out and consume action mismatch, line: {}, transfer_out_action: {}, consume_action: {}", line, action, consumeAction);
                    return Result.fail(ErrorCode.DB_STATE_MISMATCH, "unexpected action mismatch");
                }
                WalletReservation reservation = walletIdToReservation.get(consumeAction.getWalletId());
                if (reservation == null || !Objects.equals(consumeAction.getAmount(), reservation.getPendingSettle())
                        || !Objects.equals(reservation.getAssetId(), consumeAction.getAssetId())
                        || !Objects.equals(reservation.getWalletId(), consumeAction.getWalletId())) {
                    // severe db state mismatch here
                    log.error("reservation info mismatch, line: {}, consume_action: {}, reservation: {}", line, consumeAction, reservation);
                    return Result.fail(ErrorCode.DB_STATE_MISMATCH, "unexpected reservation info not match");
                }
            }
        }

        return Result.success();
    }

    private WalletTransaction createTransaction(AtomicTransactionRequestPb request, RequestInfo requestInfo) {
        return WalletTransaction.create(
                IdGenerator.generateWalletTransactionId(),
                request.getReferenceId(),
                requestInfo.serviceId,
                request.getIdempotencyKey(),
                TransactionStatus.PENDING,
                TransactionType.ATOMIC,
                requestInfo.businessType
        );
    }

    // in Atomic txn, reservation asset goes into pending settle after created
    private WalletReservation createReservation(WalletTransaction walletTransaction, RequestInfo.Line line) {
        return WalletReservation.create(
                IdGenerator.generateReservationId(),
                walletTransaction.getInitiator(),
                walletTransaction.getReferenceId() + ":" + line.walletId,
                line.walletId,
                line.walletRef,
                line.assetCode,
                line.amount,
                0L,
                0L,
                line.amount,
                0L,
                ReservationStatus.ACTIVE,
                ReservationOutcome.NOT_DONE,
                walletTransaction.getTxnId()
                );
    }

    private WalletAction createReserveAction(WalletTransaction walletTxn, RequestInfo.Line line, String reservationId) {
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                walletTxn.getTxnId(),
                line.walletId,
                line.assetCode,
                WalletBucket.AVAILABLE,
                ActionType.RESERVE,
                line.amount,
                reservationId
                );
    }

    private WalletAction createConsumeAction(WalletTransaction walletTxn, RequestInfo.Line line, String reservationId) {
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                walletTxn.getTxnId(),
                line.walletId,
                line.assetCode,
                WalletBucket.RESERVED,
                ActionType.CONSUME,
                line.amount,
                reservationId
        );
    }

    private WalletAction createTransferAction(WalletTransaction walletTxn, RequestInfo.Line line) {
        WalletBucket bucket = line.actionType == ActionType.TRANSFER_OUT ? WalletBucket.RESERVED : WalletBucket.AVAILABLE;
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                walletTxn.getTxnId(),
                line.walletId,
                line.assetCode,
                bucket,
                line.actionType,
                line.amount,
                null
                );
    }

    private WalletOutbox createOutbox(WalletTransaction walletTransaction, RequestInfo requestInfo) {
        return WalletOutbox.create(OutboxEventType.LEDGER_POST,
                walletTransaction.getReferenceId(),
                walletTransaction.getReferenceId(),
                OutboxStatus.PENDING,
                //TODO payload
                "{\"TODO\":\"todo\"}",
                0,
                OutboxHelper.nextAttempt()
                );
    }

    private Result<Void> postLedger(TransactionInfo transactionInfo) {
        List<String> walletIds = new ArrayList<>(transactionInfo.walletIdToTransferAction.keySet());
        List<WalletAccountMapping> mappings = walletAccountMappingManager.selectInWalletIds(walletIds);
        if (mappings.size() != walletIds.size()) {
            log.error("wallet_account_mapping_not_found, wallet_ids: {}, mappings: {}", walletIds, mappings);
            return Result.fail(ErrorCode.WALLET_ACCOUNT_MAPPING_NOT_FOUND, "wallet_account_mapping_not_found");
        }
        Map<String, String> walletToAccount = mappings.stream().collect(Collectors.toMap(WalletAccountMapping::getWalletId, WalletAccountMapping::getAccountRefId));

        List<LedgerEntryPb> entries = new ArrayList<>();
        for (WalletAction action : transactionInfo.walletIdToTransferAction.values()) {
            LedgerEntryPb.Builder builder = LedgerEntryPb.newBuilder();
            builder.setAccountRef(walletToAccount.get(action.getWalletId())).setAmount(action.getAmount()).setAssetId(action.getAssetId());
            switch (action.getActionType()) {
                case TRANSFER_OUT:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Debit); break;
                case TRANSFER_IN:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Credit); break;
                default:
                    throw new InvalidEnumException("invalid action type: " + action.getActionType());
            }
            entries.add(builder.build());
        }

        PostTransactionRequestPb req = PostTransactionRequestPb.newBuilder()
                .setReferenceId(transactionInfo.walletTxn.getReferenceId())
                .addAllEntries(entries).build();
        Result<PostTransactionReplyPb> result = postServiceClient.postTransaction(req);
        return Result.result(null, result);
    }

    private Result<WalletTransaction> completeExecution(TransactionInfo transactionInfo, RequestInfo requestInfo) {
        List<WalletReservation> reservationsInIdOrder = transactionInfo.reservations.stream()
                .sorted(Comparator.comparing(WalletReservation::getId)).collect(Collectors.toList());
        List<BalanceSnapshot> snapshotIdsInOrder = requestInfo.snapshotIdAndRefs.stream()
                .sorted(Comparator.comparing(BalanceSnapshot::getId)).collect(Collectors.toList());
//        Map<String, WalletAction> reservationIdToTransferOutAction = transactionInfo.walletIdToTransferAction.values().stream()
//                .filter((action) -> action.getActionType() == ActionType.TRANSFER_OUT).
//                collect(Collectors.toMap(WalletAction::getReservationId, action -> action));

        Result<Void> result = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            // consume reservation in id order
            for (WalletReservation reservation : reservationsInIdOrder) {
                WalletAction action = transactionInfo.walletIdToConsumeAction.get(reservation.getWalletId());
                if (walletReservationManager.fullySettleReservation(reservation.getId(), reservation, action.getAmount()) != 1) {
                    log.error("wallet_reservation_not_found, reservation: {}", reservation);
                    return Result.fail(ErrorCode.WALLET_RESERVATION_UPDATE_FAILED, "reservation_update_failed");
                }
            }
            // modify balance snapshot in id order
            for (BalanceSnapshot snapshot : snapshotIdsInOrder) {
                WalletAction action = transactionInfo.walletIdToTransferAction.get(snapshot.getWalletId());
                int affected;
                switch (action.getActionType()) {
                    case TRANSFER_OUT:
                        affected = balanceSnapshotManager.transferOutFromWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount());
                        break;
                    case TRANSFER_IN:
                        affected = balanceSnapshotManager.transferInToWalletId(snapshot.getId(), action.getWalletId(), action.getAssetId(), action.getAmount());
                        break;
                    default:
                        throw new InvalidEnumException("invalid action type: " + action.getActionType());
                }
                if (affected != 1) {
                    log.error("balance_snapshot_update_failed, snapshot: {}", snapshot);
                    return Result.fail(ErrorCode.BALANCE_SNAPSHOT_UPDATE_FAILED, "balance_snapshot_update_failed");
                }
            }
            if (walletTransactionManager.updateTransactionStatus(transactionInfo.walletTxn.getId(), TransactionStatus.PENDING, TransactionStatus.COMPLETED) != 1) {
                log.error("wallet_transaction_update_failed, transaction: {}", transactionInfo.walletTxn);
                return Result.fail(ErrorCode.WALLET_TRANSACTION_UPDATE_FAILED, "transaction_update_failed");
            }
            return Result.success();
        });
        if (!result.success) {
            return Result.fail(result);
        }
        WalletTransaction txn = walletTransactionManager.selectById(transactionInfo.walletTxn.getId());
        if (txn == null) {
            log.error("wallet_transaction_not_found, transaction: {}", transactionInfo.walletTxn);
            return Result.fail(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, "wallet_transaction_not_found");
        }
        return Result.success(txn);
    }

//    private Result<Void> precheck(AtomicTransactionRequestPb request, RequestInfo requestInfo, List<BalanceSnapshot> snapshots) {
//
//        Map<String, BalanceSnapshot> walletIdToSnapshot = snapshots.stream().collect(Collectors.toMap(BalanceSnapshot::getWalletId, each->each));
//        for (TransactionLinePb line : request.getLinesList()) {
//            BalanceSnapshot snapshot = walletIdToSnapshot.get(line.getWalletId());
//            if (snapshot == null) {
//                return Result.fail(ErrorCode.BALANCE_SNAPSHOT_NOT_FOUND, "balance snapshot, not found, wallet id: " + line.getWalletId());
//            }
//            if (!Objects.equals(snapshot.getAssetId(), line.getAssetCode())) {
//                return Result.fail(ErrorCode.WALLET_INFO_MISMATCH, "transaction_info_mismatch, wallet asset code not match: " + line.getAssetCode());
//            }
//            if (snapshot.getWalletStatus() != WalletStatus.OPEN) {
//                return Result.fail(ErrorCode.WALLET_NOT_AVAILABLE, "balance snapshot not open, status: " + snapshot.getWalletStatus());
//            }
//        }
//        return Result.success();
//    }



    @AllArgsConstructor
    static private class RequestInfo {
        final ServiceId serviceId;
        final BusinessType businessType;
        final List<BalanceSnapshot> snapshotIdAndRefs;
        final List<BalanceSnapshot> inSnapshotIdAndRefs;
        final List<BalanceSnapshot> outSnapshotIdAndRefs;
        final List<Line> lines;
        @AllArgsConstructor
        private static class Line {
            String walletId;
            String walletRef;
            String assetCode;
            ActionType actionType;
            Long amount;
        }
    }

    @AllArgsConstructor
    static private class TransactionInfo {
        WalletTransaction walletTxn;
        Map<String, WalletAction> walletIdToTransferAction; // just TRANSFER_IN and TRANSFER_OUT
        Map<String, WalletAction> walletIdToConsumeAction;
        List<WalletReservation> reservations;
        WalletOutbox outbox;

        static TransactionInfo create(WalletTransaction txn, List<WalletAction> actions, List<WalletReservation> reservations, WalletOutbox outbox) {
            Map<String, WalletAction> walletIdToTransferAction = actions.stream()
                    .filter(action -> action.getActionType().isTransfer())
                    .collect(Collectors.toMap(WalletAction::getWalletId, action -> action));
            Map<String, WalletAction> walletIdToConsumeAction = actions.stream()
                    .filter(action -> action.getActionType() == ActionType.CONSUME)
                    .collect(Collectors.toMap(WalletAction::getWalletId, action -> action));
            return new TransactionInfo(txn, walletIdToTransferAction, walletIdToConsumeAction, reservations, outbox);
        }
    }
    */
    private AtomicTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
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
        result = Result.requireNotNull(result);
        return replyError(result.errorCode, result.errorDetail);
    }
}
