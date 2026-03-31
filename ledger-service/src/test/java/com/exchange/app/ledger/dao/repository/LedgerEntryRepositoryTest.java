package com.exchange.app.ledger.dao.repository;

import com.exchange.app.ledger.config.DbQueryProperties;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
public class LedgerEntryRepositoryTest {
    @SpyBean
    private LedgerEntryRepository repo;
    @MockBean
    DbQueryProperties dbQueryConfig;

    @Test
    void getByTransactionId_shouldPaginateUntilBatchSmallerThanBatchSize() {
        // Arrange
        String txnId = "TXN_TEST_001"; // placeholder

        when(dbQueryConfig.getDefaultBatchSize()).thenReturn(2);

        LedgerEntry e1 = new LedgerEntry();
        e1.setId(1L);
        LedgerEntry e2 = new LedgerEntry();
        e2.setId(2L);
        LedgerEntry e3 = new LedgerEntry();
        e3.setId(3L);

        // Page 1: returns batchSize => loop continues, lastId becomes 2
        doReturn(List.of(e1, e2))
                .when(repo).getByTransactionIdAndLastId(txnId, 0L, 2);

        // Page 2: returns smaller batch => loop ends
        doReturn(List.of(e3))
                .when(repo).getByTransactionIdAndLastId(txnId, 2L, 2);

        // Act
        List<LedgerEntry> all = repo.getByTransactionId(txnId);

        // Assert
        assertThat(all).extracting(LedgerEntry::getId).containsExactly(1L, 2L, 3L);

        verify(repo, times(2)).getByTransactionIdAndLastId(anyString(), anyLong(), anyInt());
        verify(repo).getByTransactionIdAndLastId(txnId, 0L, 2);
        verify(repo).getByTransactionIdAndLastId(txnId, 2L, 2);
    }

    @Test
    void getByTransactionId_shouldStopImmediatelyWhenFirstBatchIsEmpty() {
        // Arrange
        String txnId = "TXN_TEST_002"; // placeholder

        when(dbQueryConfig.getDefaultBatchSize()).thenReturn(3);

        doReturn(List.of())
                .when(repo).getByTransactionIdAndLastId(txnId, 0L, 3);

        // Act
        List<LedgerEntry> all = repo.getByTransactionId(txnId);

        // Assert
        assertThat(all).isEmpty();
        verify(repo).getByTransactionIdAndLastId(txnId, 0L, 3);
        verify(repo, times(1)).getByTransactionIdAndLastId(anyString(), anyLong(), anyInt());
    }
}