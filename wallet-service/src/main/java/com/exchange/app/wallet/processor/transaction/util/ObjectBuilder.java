package com.exchange.app.wallet.processor.transaction.util;

import com.exchange.app.wallet.exception.InvalidEnumException;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletBucket;
import com.exchange.app.wallet.po.enums.transaction.*;
import com.exchange.app.wallet.po.transaction.WalletAction;
import com.exchange.app.wallet.po.transaction.WalletReservation;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Results;
import com.exchange.app.wallet.utils.IdGenerator;
import com.exchange.app.wallet.utils.OutboxHelper;
import com.exchange.common.outbox.po.Outbox;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.ledger.common.LedgerDirectionPb;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.proto.wallet.wallet.TransactionLinePb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ObjectBuilder {
    public static WalletTransaction createTransaction(TransactionType transactionType, String referenceId, ServiceId serviceId, String idempotencyKey, BusinessType businessType) {
        return WalletTransaction.create(
                IdGenerator.generateWalletTransactionId(),
                referenceId,
                serviceId,
                idempotencyKey,
                TransactionStatus.PENDING,
                transactionType,
                businessType
        );
    }

    public static WalletReservation createReservation(WalletTransaction walletTransaction, String walletId, TransactionLinePb line) {
        switch (line.getOperationType()) {
            case OperationTypePb_Debit:
                // in Atomic txn, reservation asset goes into pending settle after created
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + walletId,
                        walletId,
                        line.getWalletRef(),
                        line.getAssetCode(),
                        line.getAmount(),
                        0L,
                        0L,
                        line.getAmount(),
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            case OperationTypePb_Reserve:
                return  WalletReservation.create(
                        IdGenerator.generateReservationId(),
                        walletTransaction.getInitiator(),
                        walletTransaction.getReferenceId() + ":" + walletId,
                        walletId,
                        line.getWalletRef(),
                        line.getAssetCode(),
                        line.getAmount(),
                        line.getAmount(),
                        0L,
                        0L,
                        0L,
                        ReservationStatus.ACTIVE,
                        ReservationOutcome.NOT_DONE,
                        walletTransaction.getTxnId()
                );
            default: throw new InvalidEnumException("OperationType_Reserve not supported: " + line.getOperationType());
        }
    }

    public static List<WalletAction> createActionsFromLine(String txnId, String walletId, String reservationId, TransactionLinePb line) {
        List<WalletAction> actions = new ArrayList<>();
        switch (line.getOperationType()) {
            case OperationTypePb_Reserve:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                break;
            case OperationTypePb_Earmark:
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, walletId, null, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Release:
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.RELEASE, line));
                break;
            case OperationTypePb_Debit:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.RESERVE, line));
                actions.add(createAction(txnId, walletId, reservationId, WalletBucket.RESERVED, ActionType.CONSUME, line));
                actions.add(createAction(txnId, walletId, null, WalletBucket.RESERVED, ActionType.TRANSFER_OUT, line));
                break;
            case OperationTypePb_Credit:
                actions.add(createAction(txnId, walletId, null, WalletBucket.AVAILABLE, ActionType.TRANSFER_IN, line));
                break;
            default:
                throw new InvalidEnumException("invalid operation type: " + line.getOperationType());
        }
        return actions;
    }

    public static WalletAction createAction(String txnId, String walletId, String reservationId, WalletBucket bucket, ActionType actionType, TransactionLinePb line) {
        return WalletAction.create(
                IdGenerator.generateWalletActionId(),
                txnId,
                walletId,
                line.getAssetCode(),
                bucket,
                actionType,
                line.getAmount(),
                reservationId
        );
    }

    public static Result<Outbox> createOutbox(WalletTransaction txn, List<WalletAction> actions, List<WalletAccountMapping> mappings) {
        Map<String, String> walletToAccount = mappings.stream().collect(Collectors.toMap(WalletAccountMapping::getWalletId, WalletAccountMapping::getAccountRefId));

        List<LedgerEntryPb> entries = new ArrayList<>();
        for (WalletAction action : actions) {
            LedgerEntryPb.Builder builder = LedgerEntryPb.newBuilder();
            builder.setAccountRef(walletToAccount.get(action.getWalletId())).setAmount(action.getAmount()).setAssetId(action.getAssetId());
            switch (action.getActionType()) {
                case TRANSFER_OUT:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Debit);
                    break;
                case TRANSFER_IN:
                    builder.setDirection(LedgerDirectionPb.LedgerDirection_Credit);
                    break;
                default:
                    continue;
            }
            entries.add(builder.build());
        }

        PostTransactionRequestPb req = PostTransactionRequestPb.newBuilder()
                .setReferenceId(txn.getTxnId())
                .addAllEntries(entries).build();
        Outbox outbox = OutboxHelper.fromPostTransactionRequest(req, txn);
        return Results.success(outbox);
    }
}
