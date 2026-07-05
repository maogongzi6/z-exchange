package com.exchange.app.ledger.dao.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.app.ledger.config.DbQueryProperties;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.exception.InvalidValueException;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.common.db.manager.DbBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@EnableConfigurationProperties(DbQueryProperties.class)
public class LedgerEntryRepository extends DbBaseRepository<LedgerEntry, LedgerEntryMapper> {
    private final DbQueryProperties dbQueryConfig;

    public LedgerEntryRepository(LedgerEntryMapper mapper, DbQueryProperties dbQueryConfig) {
        super(mapper);
        this.dbQueryConfig = dbQueryConfig;
    }

    public List<LedgerEntry> getByTransactionId(String txnId) {
        List<LedgerEntry> result = new ArrayList<>();
        long lastId = 0L;
        List<LedgerEntry> batch;
        int batchSize = dbQueryConfig.getDefaultBatchSize();

        do {
            batch = getByTransactionIdAndLastId(txnId, lastId, batchSize);
            result.addAll(batch);

            if (!batch.isEmpty()) {
                lastId = batch.get(batch.size() - 1).getId();
            }

            log.debug("Fetched batch of size {} for transactionId {}", batch.size(), txnId);
        } while (batch.size() == batchSize);

        log.debug("Completed fetching all ledger entries for transactionId {}", txnId);
        return result;
    }

    public int batchInsert(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            throw new InvalidValueException("ledger entry batch insert list must not be empty");
        }
        return mapper.batchInsert(entries);
    }

    // set to protected scope for unit test
    protected List<LedgerEntry> getByTransactionIdAndLastId(String txnId, long lastId, int size) {
        return mapper.selectList(
                Wrappers.<LedgerEntry>lambdaQuery()
                        .eq(LedgerEntry::getTxnId, txnId)
                        .gt(LedgerEntry::getId, lastId)
                        .orderByAsc(LedgerEntry::getId)
                        .last("LIMIT " + size)
        );
    }
}
