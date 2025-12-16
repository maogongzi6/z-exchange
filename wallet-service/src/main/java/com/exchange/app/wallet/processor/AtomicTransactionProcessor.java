package com.exchange.app.wallet.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.wallet.client.PostServiceClient;
import com.exchange.app.wallet.dao.manager.*;
import com.exchange.app.wallet.dao.mapper.*;
import com.exchange.app.wallet.exception.InvalidEnumException;
import com.exchange.app.wallet.po.enums.WalletBucket;
import com.exchange.app.wallet.po.enums.outbox.OutboxEventType;
import com.exchange.app.wallet.po.enums.outbox.OutboxStatus;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.outbox.WalletOutbox;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.PbErrorBuilder;
import com.exchange.app.wallet.result.Result;
import com.exchange.app.wallet.utils.*;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb;
import com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb;
import com.exchange.proto.wallet.wallet.TransactionLinePb;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AtomicTransactionProcessor {
    final private BalanceSnapshotManager balanceSnapshotManager;
    final private WalletAccountMappingManager walletAccountMappingManager;
    private final WalletTransactionManager walletTransactionManager;
    private final WalletOutboxManager walletOutboxManager;
    private final WalletReservationManager walletReservationManager;
    private final WalletActionManager walletActionManager;

    private final PostServiceClient postServiceClient;
    private final TransactionTemplate transactionTemplate;

    public AtomicTransactionReplyPb executeAtomic(AtomicTransactionRequestPb request) {
        Result<RequestInfo> infoResult = validateAndGetRequestInfo(request);
        if (!infoResult.success) {
            return replyError(infoResult);
        }
        RequestInfo requestInfo = infoResult.value;
        WalletTransaction walletTxn = walletTransactionManager.selectByIdempotencyKey(requestInfo.serviceId, request.getIdempotencyKey());
        Result<TransactionInfo> txnInfoResult;
        // idempotency check
        if (walletTxn == null) {
            txnInfoResult = createWalletTxn(request, requestInfo);
        } else if (walletTxn.getTxnStatus() == TransactionStatus.PENDING) {
            txnInfoResult = getTransactionInfo(request, requestInfo, walletTxn);
        } else {
            return replySuccess(walletTxn, "txn already executed");
        }
        if (!txnInfoResult.success) {
            return replyError(txnInfoResult);
        }
        TransactionInfo transactionInfo = txnInfoResult.value;
        Result<Void> result = postLedger(transactionInfo);
        if (!result.success) {
            return replyError(result);
        }
        Result<WalletTransaction> txnResult = completeExecution(transactionInfo, requestInfo);
        if (!txnResult.success) {
            return replyError(result);
        }
        // outbox should not block main flow
        if (walletOutboxManager.finishOutbox(transactionInfo.outbox) != 1) {
            log.error("finish outbox failed, outbox={}", transactionInfo.outbox);
        }

        return replySuccess(txnResult.value, "success");
    }

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
            ActionType actionType = EnumMappers.actionTypePbMapper.to(line.getActionType());
            // validate action type and amount
            if (actionType != ActionType.TRANSFER_OUT && actionType != ActionType.TRANSFER_IN) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid action type: " + line);
            }
            if (line.getAmount() <= 0) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid amount: " + line);
            }

            Map<String, Long> targetAssetToAmount = actionType == ActionType.TRANSFER_OUT ? outAssetToAmount : inAssetToAmount;
            if (!targetAssetToAmount.containsKey(line.getAssetCode())) {
                targetAssetToAmount.put(line.getAssetCode(), 0L);
            }
            targetAssetToAmount.put(line.getAssetCode(), line.getAmount() + line.getAmount());

            // each wallet only have one line
            if (walletRefs.contains(line.getWalletRef())) {
                return  Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "duplicate wallet ref: " + line);
            }
            walletRefs.add(line.getWalletRef());
        }

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
            ActionType actionType = EnumMappers.actionTypePbMapper.to(line.getActionType());
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
            String reservationId;
            if (line.actionType == ActionType.TRANSFER_OUT) {
                WalletReservation reservation = createReservation(walletTxn, line);
                reservations.add(reservation);
                reservationId = reservation.getReservationId();
                actions.add(createReserveAction(walletTxn, line, reservationId));
            } else {
                // transfer in action do not have reservation id
                reservationId = "";
            }
            actions.add(createTransferAction(walletTxn, line, reservationId));
        }

        WalletOutbox walletOutbox = createOutbox(walletTxn, requestInfo);

        Result<Void> result = createWalletTxnDbTransaction(walletTxn, actions, reservations, walletOutbox, requestInfo);
        if (!result.success) {
            return Result.fail(result);
        }
        Map<String, WalletAction> walletIdToTransferAction = actions.stream()
                .filter(action -> action.getActionType() != ActionType.RESERVE)
                .collect(Collectors.toMap(WalletAction::getWalletId, action -> action));

        // fill id into reservations
        List<WalletReservation> reservationWithId = walletReservationManager.selectByTxnId(walletTxn.getTxnId(), new LambdaQueryWrapper<>() {{eq(WalletReservation::getReservationStatus, TransactionStatus.PENDING);}});
        if (reservationWithId.size() != reservations.size()) {
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "reservation size mismatch: " + reservations.size() + ", " + reservationWithId.size());
        }
        return Result.success(new TransactionInfo(walletTxn, walletIdToTransferAction, reservationWithId, walletOutbox));
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
        List<WalletAction> transferActions = walletActionManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{
            in(WalletAction::getActionType, ActionType.TRANSFER_IN, ActionType.TRANSFER_OUT);
        }});
        List<WalletReservation> reservations = walletReservationManager.selectByTxnId(txn.getTxnId(), new LambdaQueryWrapper<>() {{eq(WalletReservation::getReservationStatus, TransactionStatus.PENDING);}});
        WalletOutbox outbox = walletOutboxManager.selectByIdempotencyKey(txn.getReferenceId());
        Map<String, WalletAction> walletIdToTransferAction = transferActions.stream().collect(Collectors.toMap(WalletAction::getWalletId, action -> action));
        TransactionInfo transactionInfo = new TransactionInfo(txn, walletIdToTransferAction, reservations, outbox);
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
        if (transactionInfo.reservations.size() != requestInfo.outSnapshotIdAndRefs.size()) {
            log.error("reservations: {}, out_wallet_ids: {}", transactionInfo, requestInfo.outSnapshotIdAndRefs);
            return Result.fail(ErrorCode.WALLET_RESERVATION_NOT_FOUND, "unexpected wallet_reservation not found");
        }

        Map<String, WalletReservation> walletIdToReservation = transactionInfo.reservations.stream().collect(Collectors.toMap(WalletReservation::getWalletId, reservation -> reservation));
        for (RequestInfo.Line line : requestInfo.lines) {
            WalletAction action = transactionInfo.walletIdToTransferAction.get(line.walletId);
            if (action == null) {
                log.error("action not found, walletId: {}", line.walletId);
                return Result.fail(ErrorCode.WALLET_ACTION_NOT_FOUND, "unexpected action not found");
            }
            if (line.actionType != action.getActionType()
                    || !Objects.equals(line.assetCode, action.getAssetId())
                    || !Objects.equals(line.amount, action.getAmount())) {
                log.error("action info not match, line: {}, action: {}", line, action);
                return Result.fail(ErrorCode.WALLET_ACTION_INVALID, "unexpected action info not match");
            }
            if (line.actionType == ActionType.TRANSFER_OUT) {
                // each transfer-out should have one reservation
                WalletReservation reservation = walletIdToReservation.get(line.walletId);
                if (reservation == null || !Objects.equals(line.amount, reservation.getPendingSettleAmount())) {
                    log.error("reservation info not match, line: {}, reservation: {}", line, reservation);
                    return Result.fail(ErrorCode.WALLET_RESERVATION_INVALID, "unexpected reservation info not match");
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
        return  WalletReservation.create(
                IdGenerator.generateReservationId(),
                walletTransaction.getInitiator(),
                walletTransaction.getReferenceId() + ":" + line.walletId,
                line.walletId,
                line.assetCode,
                0L,
                0L,
                line.amount,
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

    private WalletAction createTransferAction(WalletTransaction walletTxn, RequestInfo.Line line, String reservationId) {
        WalletBucket bucket = line.actionType == ActionType.TRANSFER_OUT ? WalletBucket.RESERVED : WalletBucket.AVAILABLE;
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                walletTxn.getTxnId(),
                line.walletId,
                line.assetCode,
                bucket,
                line.actionType,
                line.amount,
                reservationId
                );
    }

    private WalletOutbox createOutbox(WalletTransaction walletTransaction, RequestInfo requestInfo) {
        return WalletOutbox.create(OutboxEventType.LEDGER_POST,
                walletTransaction.getReferenceId(),
                walletTransaction.getReferenceId(),
                OutboxStatus.PENDING,
                //TODO payload
                "{\"name\":\"a\"}",
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
        Map<String, WalletAction> reservationIdToTransferOutAction = transactionInfo.walletIdToTransferAction.values().stream()
                .filter((action) -> action.getActionType() == ActionType.TRANSFER_OUT).
                collect(Collectors.toMap(WalletAction::getReservationId, action -> action));

        Result<Void> result = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            // consume reservation in id order
            for (WalletReservation reservation : reservationsInIdOrder) {
                WalletAction action = reservationIdToTransferOutAction.get(reservation.getReservationId());
                if (walletReservationManager.fullyConsumeReservation(reservation.getId(), reservation, action.getAmount()) != 1) {
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

    private AtomicTransactionReplyPb replySuccess(WalletTransaction txn, String detail) {
        return AtomicTransactionReplyPb.newBuilder()
                .setTransactionId(txn.getTxnId())
                .setStatus(EnumMappers.transactionStatusPbMapper.from(txn.getTxnStatus()))
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
        //List<WalletAction> transferActions; // just TRANSFER_IN and TRANSFER_OUT, not include HOLD action
        Map<String, WalletAction> walletIdToTransferAction; // just TRANSFER_IN and TRANSFER_OUT, not include HOLD action
        List<WalletReservation> reservations;
        WalletOutbox outbox;
    }
}
