package com.exchange.app.ledger.processor.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.dao.manager.LedgerTxnManager;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.PbErrorBuilder;
import com.exchange.app.ledger.result.Result;
import com.exchange.app.ledger.utils.DbTransactionHelper;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.app.ledger.utils.EnumMappers;
import com.exchange.app.ledger.utils.EntryHelper;
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
    final private LedgerTxnMapper ledgerTxnMapper;
    final private AccountMapper accountMapper;

    final private LedgerTxnManager ledgerTxnManager;

    final private TransactionTemplate transactionTemplate;

    public PostTransactionReplyPb postTransaction(PostTransactionRequestPb req) {

        Result<Void> result = validateReq(req);
        if (!Result.isSuccess(result)) {
            return replyError(result);
        }

        String txnId = IdGenerator.generateLedgerTxnId();

        LedgerTxn ledgerTxn = LedgerTxn.create(txnId, req.getReferenceId(), "TODO");
        List<LedgerEntry> entries = req.getEntriesList().stream().map(entry -> EntryHelper.protoToDto(ledgerTxn.getTxnId(), IdGenerator.generateLedgerEntryId(), entry)).collect(Collectors.toList());
        Set<String> accountIds = entries.stream().map(LedgerEntry::getAccountId).collect(Collectors.toSet());
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Account::getAccountId).in(Account::getAccountId, accountIds);
        List<String> accountIdFromDb = accountMapper.selectList(wrapper).stream().map(Account::getAccountId).collect(Collectors.toList());
        wrapper.clear();
        if (accountIdFromDb.size() != accountIds.size()) {
            accountIdFromDb.forEach(accountIds::remove);
            return replyError(ErrorCode.ACCOUNT_NOT_FOUND, accountIds.toString());
        }

        result = DbTransactionHelper.executeWithResult(transactionTemplate, TransactionDefinition.PROPAGATION_REQUIRED, () -> {
            if (ledgerTxnManager.insertIgnore(ledgerTxn) == 0) {
                log.info("ledger txn already exists");
                return Result.success();
            }
            if (ledgerEntryMapper.batchInsert(entries) < entries.size()) {
                return Result.fail(ErrorCode.LEDGER_DUPLICATED, "unexpected duplicated_ledger_entry");
            }
            return Result.success();
        });
        if (!Result.isSuccess(result)) {
            return replyError(result);
        }

        return replySuccess("success");
    }

    private Result<Void> validateReq(PostTransactionRequestPb req) {
        Map<String, Long> debitSumMap = new HashMap<>(), creditSumMap = new HashMap<>();
        int debitCount = 0, creditCount = 0;

        for (LedgerEntryPb entry : req.getEntriesList()) {
            if (entry.getAmount() == 0) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "zero_amount: " + entry);
            }
            Direction d = EnumMappers.directionPbMapper.to(entry.getDirection());
            String assetId = entry.getAssetId();
            if (d == null || d == Direction.UNKNOWN) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "invalid_direction: " + Direction.UNKNOWN);
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
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }

        if (debitSumMap.size() != creditSumMap.size()) {
            return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }
        for (Map.Entry<String, Long> entry: debitSumMap.entrySet()) {
            String assetId = entry.getKey();
            Long debitAmount = entry.getValue();
            if (!Objects.equals(creditSumMap.get(assetId), debitAmount)) {
                return Result.fail(ErrorCode.INVALID_REQUEST_PARAMETER,("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap));
            }
        }
        return Result.success();
    }

    private PostTransactionReplyPb replySuccess(String detail) {
        return replyError(ErrorCode.SUCCESS, detail);
    }

    private PostTransactionReplyPb replyError(ErrorCode errorCode, String detail) {
        PostTransactionReplyPb.Builder builder = PostTransactionReplyPb.newBuilder();
        return builder.setError(PbErrorBuilder.build(errorCode, detail)).build();
    }

    private PostTransactionReplyPb replyError(Result<?> result) {
        result = Result.requireNotNull(result);
        return replyError(result.errorCode, result.errorDetail);
    }
}
