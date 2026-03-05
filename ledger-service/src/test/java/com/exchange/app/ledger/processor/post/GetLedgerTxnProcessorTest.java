package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.dao.repository.LedgerEntryRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.common.utils.result.Result;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class GetLedgerTxnProcessorTest {

    @Mock
    private LedgerTxnRepository ledgerTxnRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private GetLedgerTxnProcessor getLedgerTxnProcessor;

    public GetLedgerTxnProcessorTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnInvalidParameterErrorWhenLookupValueIsEmpty() {
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, null, false);

        assertFalse(result.success);
        assertEquals(ErrorCode.INVALID_REQUEST_PARAMETER, result.errorCode);
        assertEquals("lookup_value is required", result.errorDetail);
    }

    @Test
    void shouldReturnInvalidParameterErrorWhenLookupTypeIsNull() {
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(null, "testValue", false);

        assertFalse(result.success);
        assertEquals(ErrorCode.INVALID_REQUEST_PARAMETER, result.errorCode);
        assertEquals("lookup_type is required", result.errorDetail);
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByTxnId() {
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", false);

        assertFalse(result.success);
        assertEquals(ErrorCode.LEDGER_NOT_FOUND, result.errorCode);
        assertEquals("ledger not found", result.errorDetail);
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByRefId() {
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertFalse(result.success);
        assertEquals(ErrorCode.LEDGER_NOT_FOUND, result.errorCode);
        assertEquals("ledger not found", result.errorDetail);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByTxnIdWithoutEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", false);

        assertTrue(result.success);
        assertEquals(mockTxn, result.value.txn);
        assertNull(result.value.entries);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByRefIdWithoutEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertTrue(result.success);
        assertEquals(mockTxn, result.value.txn);
        assertNull(result.value.entries);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundWithEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        LedgerEntry mockEntry = new LedgerEntry();
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(mockTxn);
        when(ledgerEntryRepository.getByTransactionId("testTxnId")).thenReturn(Collections.singletonList(mockEntry));

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", true);

        assertTrue(result.success);
        assertEquals(mockTxn, result.value.txn);
        assertEquals(1, result.value.entries.size());
        assertEquals(mockEntry, result.value.entries.get(0));
    }

    @Test
    void shouldReturnInternalErrorWhenEntriesNotFoundForTxnId() {
        LedgerTxn mockTxn = new LedgerTxn();
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(mockTxn);
        when(ledgerEntryRepository.getByTransactionId("testTxnId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", true);

        assertFalse(result.success);
        assertEquals(ErrorCode.INTERNAL_ERROR, result.errorCode);
        assertEquals("internal error", result.errorDetail);
    }
}