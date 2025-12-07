package com.example.processor.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.exception.AccountException;
import com.example.exception.CommonException;
import com.example.exception.ErrorCode;
import com.example.exception.LedgerException;
import com.example.dao.mapper.AccountMapper;
import com.example.dao.mapper.LedgerEntryMapper;
import com.example.dao.mapper.LedgerTxnMapper;
import com.example.po.account.Account;
import com.example.po.enums.Direction;
import com.example.po.ledger.LedgerEntry;
import com.example.po.ledger.LedgerTxn;
import com.example.pojo.ledger.post.PostTransactionReply;
import com.example.pojo.ledger.post.PostTransactionRequest;
import com.example.utils.EnumConvertHelper;
import com.example.utils.EntryHelper;
import com.example.utils.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
    final private TransactionTemplate transactionTemplate;

    public PostTransactionReply postTransaction(PostTransactionRequest req) {
        validateReq(req);

        String txnId = IdGenerator.generateLedgerTxnId();

        LedgerTxn ledgerTxn = LedgerTxn.create(txnId, req.getReferenceId(), "TODO");
        List<LedgerEntry> entries = req.getEntries().stream().map(entry -> EntryHelper.pojoToPo(ledgerTxn.getTxnId(), IdGenerator.generateLedgerEntryId(), entry)).collect(Collectors.toList());
        Set<String> accountIds = entries.stream().map(LedgerEntry::getAccountId).collect(Collectors.toSet());
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Account::getAccountId).in(Account::getAccountId, accountIds);
        List<String> accountIdFromDb = accountMapper.selectList(wrapper).stream().map(Account::getAccountId).collect(Collectors.toList());
        wrapper.clear();
        if (accountIdFromDb.size() != accountIds.size()) {
            accountIdFromDb.forEach(accountIds::remove);
            throw AccountException.accountNotFound(accountIds.toString());
        }

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        try {
            transactionTemplate.execute((transactionStatus -> {
                try {

                    if (ledgerTxnMapper.insert(ledgerTxn) == 0) {
                        throw LedgerException.duplicatedLedger("duplicated_ledger_txn");
                    }
                    if (ledgerEntryMapper.batchInsert(entries) < entries.size()) {
                        throw LedgerException.duplicatedLedger("duplicated_ledger_entry");
                    }
                } catch (Exception e) {
                    transactionStatus.setRollbackOnly();
                    if (e instanceof DuplicateKeyException) {
                        throw (LedgerException) LedgerException.duplicatedLedger("duplicate_ledger").initCause(e);
                    } else {
                        throw e;
                    }
                }
                return null;
            }));
        } catch (LedgerException e) {
            if (e.getCode() == ErrorCode.LEDGER_DUPLICATED) {
                // ignore here
                log.warn("Duplicated ledger: {}", e.getMessage());
            } else {
                throw e;
            }
        }

        return new PostTransactionReply(ErrorCode.SUCCESS.code, ErrorCode.SUCCESS.message);
    }

    private void validateReq(PostTransactionRequest req) {
        Map<String, Long> debitSumMap = new HashMap<>(), creditSumMap = new HashMap<>();
        int debitCount = 0, creditCount = 0;

        for (com.example.pojo.ledger.post.LedgerEntry entry : req.getEntries()) {
            if (entry.getAmount() == 0) {
                throw CommonException.invalidRequestParameter("zero_amount: " + entry);
            }
            Direction d = EnumConvertHelper.directionPbToPo(entry.getDirection());
            String assetId = entry.getAssetId();
            if (d == null || d == Direction.UNKNOWN) {
                throw CommonException.invalidRequestParameter("invalid_direction: " + Direction.UNKNOWN);
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
            throw CommonException.invalidRequestParameter("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }

        if (debitSumMap.size() != creditSumMap.size()) {
            throw CommonException.invalidRequestParameter("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
        }
        debitSumMap.forEach((assetId, amount) -> {
            if (!Objects.equals(creditSumMap.get(assetId), amount)) {
                throw CommonException.invalidRequestParameter("imbalanced_entry: debit: " + debitSumMap + ", credit: " + creditSumMap);
            }
        });
    }
}
