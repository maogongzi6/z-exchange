package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.dao.repository.AccountRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.db.utils.DbTransactionHelper;
import com.exchange.common.utils.result.Result;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.app.ledger.utils.EnumMappers;
import com.exchange.app.ledger.utils.IdGenerator;
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
public class PostLedgerProcessor {
    final private LedgerEntryMapper ledgerEntryMapper;

    final private LedgerTxnRepository ledgerTxnManager;

    final private TransactionTemplate transactionTemplate;
    private final AccountRepository accountManager;

    public PostTransactionReplyPb postTransaction(PostTransactionRequestPb req) {

        Result<Void> result = validateReq(req);
        if (result.isFailed()) {
            log.error("invalid request, {}, {}", req, result);
            return replyError(Results.getErrorCode(result), result.errorDetail);
        }

        String txnId = IdGenerator.generateLedgerTxnId();
        LedgerTxn ledgerTxn = LedgerTxn.create(txnId, req.getReferenceId(), "TODO");
//        List<LedgerEntry> entries = req.getEntriesList().stream().map(entry -> EntryHelper.protoToDto(ledgerTxn.getTxnId(), IdGenerator.generateLedgerEntryId(), entry)).collect(Collectors.toList());
//        Set<String> accountIds = entries.stream().map(LedgerEntry::getAccountId).collect(Collectors.toSet());
        List<String> accountRefs = req.getEntriesList().stream().map(LedgerEntryPb::getAccountRef).collect(Collectors.toList());
        Map<String, String> accountRefToAccountId = accountManager.getAccountIdInRef(accountRefs).stream()
                .collect(Collectors.toMap(Account::getReferenceId, Account::getAccountId));
        if (accountRefToAccountId.size() != accountRefs.size()) {
            Set<String> notFound = new HashSet<>(accountRefs) {{removeAll(accountRefToAccountId.keySet());}};
            log.error("account not found, notFoundRefs={}", notFound);
            return replyError(ErrorCode.ACCOUNT_NOT_FOUND, "account_not_found, refs: " + notFound);
        }

        List<LedgerEntry> entries = new ArrayList<>();
        for (LedgerEntryPb entryPb : req.getEntriesList()) {
            entries.add(createLedgerEntry(txnId, entryPb, accountRefToAccountId.get(entryPb.getAccountRef())));
        }
        result = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            if (ledgerTxnManager.insertIgnore(ledgerTxn) == 0) {
                log.info("ledger txn already exists, {}", ledgerTxn);
                return Results.success();
            }
            if (ledgerEntryMapper.batchInsert(entries) < entries.size()) {
                log.error("unexpected ledger txn already exists, {}", ledgerTxn);
                // should be a server error?
                return Results.fail(ErrorCode.SERVER_ERROR, "unexpected duplicated_ledger_entry");
            }
            return Results.success();
        });
        if (result.isFailed()) {
            return replyError(Results.getErrorCode(result), result.errorDetail);
        }

        return replySuccess(req.getReferenceId(), "success");
    }

    private Result<Void> validateReq(PostTransactionRequestPb req) {
        Map<String, Long> debitSumMap = new HashMap<>(), creditSumMap = new HashMap<>();
        int debitCount = 0, creditCount = 0;

        for (LedgerEntryPb entry : req.getEntriesList()) {
            if (entry.getAmount() == 0) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "zero_amount: " + entry);
            }
            Direction d = EnumMappers.directionPbMapper.to(entry.getDirection());
            String assetId = entry.getAssetId();
            if (d == null || d == Direction.UNKNOWN) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_direction: " + Direction.UNKNOWN);
            }
            if (d == Direction.DEBIT) {
                debitCount++;
            } else {
                creditCount++;
            }
            Map<String, Long> targetSumMap = d == Direction.DEBIT ? debitSumMap : creditSumMap;
            if (!targetSumMap.containsKey(assetId)) {
                targetSumMap.put(assetId, 0L);
            }
            targetSumMap.put(assetId, targetSumMap.get(assetId) + entry.getAmount());
        }

        if (debitCount == 0 || creditCount == 0) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }

        if (debitSumMap.size() != creditSumMap.size()) {
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }
        for (Map.Entry<String, Long> entry: debitSumMap.entrySet()) {
            String assetId = entry.getKey();
            Long debitAmount = entry.getValue();
            if (!Objects.equals(creditSumMap.get(assetId), debitAmount)) {
                return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER,("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap));
            }
        }
        return Results.success();
    }

    private LedgerEntry createLedgerEntry(String txnId, LedgerEntryPb entryPb, String accountId) {
        return LedgerEntry.create(
                IdGenerator.generateLedgerEntryId(),
                txnId,
                entryPb.getAssetId(),
                accountId,
                entryPb.getAmount(),
                EnumMappers.directionPbMapper.to(entryPb.getDirection())
        );
    }

    private PostTransactionReplyPb replySuccess(String walletReferenceId, String detail) {
        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        return builder.setReferenceId(walletReferenceId).setError(PbErrorBuilder.build(ErrorCode.SUCCESS, detail)).build();
    }

    private PostTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }
}
