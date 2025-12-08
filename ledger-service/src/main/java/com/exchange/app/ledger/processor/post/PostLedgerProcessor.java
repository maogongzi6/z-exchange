package com.exchange.app.ledger.processor.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.exception.AccountException;
import com.exchange.app.ledger.exception.CommonException;
import com.exchange.app.ledger.exception.ErrorCode;
import com.exchange.app.ledger.exception.LedgerException;
import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.proto.ledger.post.LedgerEntryPb;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import com.exchange.app.ledger.utils.EnumConvertHelper;
import com.exchange.app.ledger.utils.EntryHelper;
import com.exchange.app.ledger.utils.IdGenerator;
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

    public PostTransactionReplyPb postTransaction(PostTransactionRequestPb req) {
        validateReq(req);

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

        return PostTransactionReplyPb.newBuilder().setCode(ErrorCode.SUCCESS.code).setMsg(ErrorCode.SUCCESS.message).build();
    }

    private void validateReq(PostTransactionRequestPb req) {
        Map<String, Long> debitSumMap = new HashMap<>(), creditSumMap = new HashMap<>();
        int debitCount = 0, creditCount = 0;

        for (LedgerEntryPb entry : req.getEntriesList()) {
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
