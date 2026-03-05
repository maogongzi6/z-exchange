package com.exchange.app.ledger.dao.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exchange.app.ledger.config.DbQueryConfig;
import com.exchange.app.ledger.dao.mapper.LedgerEntryMapper;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@DataJpaTest
public class LedgerEntryRepositoryTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockBean
    private LedgerEntryMapper ledgerEntryMapper;

    @MockBean
    private DbQueryConfig dbQueryConfig;

    @Test
    void testGetByTransactionId_NoEntriesFound() {
        String transactionId = "txn-123";
        long lastId = 0L;

        when(dbQueryConfig.getDefaultBatchSize()).thenReturn(10);
        when(ledgerEntryMapper.selectList(Wrappers.<LedgerEntry>lambdaQuery()
                .eq(LedgerEntry::getTxnId, transactionId)
                .gt(LedgerEntry::getId, lastId)
                .orderByAsc(LedgerEntry::getId)
                .last("LIMIT " + 10))).thenReturn(Collections.emptyList());

        List<LedgerEntry> result = ledgerEntryRepository.getByTransactionId(transactionId);

        assertEquals(0, result.size(), "Expected no entries to be found");
    }

    @Test
    void testGetByTransactionId_SingleBatch() {
        String transactionId = "txn-123";
        List<LedgerEntry> mockEntries = Arrays.asList(
                new LedgerEntry(1L, transactionId, "asset-1", "account-1", 1000L, null),
                new LedgerEntry(2L, transactionId, "asset-1", "account-2", 2000L, null)
        );

        when(dbQueryConfig.getDefaultBatchSize()).thenReturn(10);
        when(ledgerEntryMapper.selectList(Wrappers.<LedgerEntry>lambdaQuery()
                .eq(LedgerEntry::getTxnId, transactionId)
                .gt(LedgerEntry::getId, 0L)
                .orderByAsc(LedgerEntry::getId)
                .last("LIMIT " + 10))).thenReturn(mockEntries);

        List<LedgerEntry> result = ledgerEntryRepository.getByTransactionId(transactionId);

        assertEquals(2, result.size(), "Expected two entries to be returned");
        assertEquals(mockEntries, result, "Result must match the mock data");
    }

    @Test
    void testGetByTransactionId_MultipleBatches() {
        String transactionId = "txn-123";
        List<LedgerEntry> firstBatch = Arrays.asList(
                new LedgerEntry(1L, transactionId, "asset-1", "account-1", 1000L, null),
                new LedgerEntry(2L, transactionId, "asset-2", "account-1", 1500L, null)
        );
        List<LedgerEntry> secondBatch = Arrays.asList(
                new LedgerEntry(3L, transactionId, "asset-1", "account-3", 2000L, null)
        );

        when(dbQueryConfig.getDefaultBatchSize()).thenReturn(2);

        when(ledgerEntryMapper.selectList(Wrappers.<LedgerEntry>lambdaQuery()
                .eq(LedgerEntry::getTxnId, transactionId)
                .gt(LedgerEntry::getId, 0L)
                .orderByAsc(LedgerEntry::getId)
                .last("LIMIT " + 2))).thenReturn(firstBatch);

        when(ledgerEntryMapper.selectList(Wrappers.<LedgerEntry>lambdaQuery()
                .eq(LedgerEntry::getTxnId, transactionId)
                .gt(LedgerEntry::getId, 2L)
                .orderByAsc(LedgerEntry::getId)
                .last("LIMIT " + 2))).thenReturn(secondBatch);

        when(ledgerEntryMapper.selectList(Wrappers.<LedgerEntry>lambdaQuery()
                .eq(LedgerEntry::getTxnId, transactionId)
                .gt(LedgerEntry::getId, 3L)
                .orderByAsc(LedgerEntry::getId)
                .last("LIMIT " + 2))).thenReturn(Collections.emptyList());

        List<LedgerEntry> result = ledgerEntryRepository.getByTransactionId(transactionId);

        List<LedgerEntry> expectedEntries = new ArrayList<>();
        expectedEntries.addAll(firstBatch);
        expectedEntries.addAll(secondBatch);

        assertEquals(3, result.size(), "Expected three entries to be returned");
        assertEquals(expectedEntries, result, "Result must match the combined mock data");
    }
}