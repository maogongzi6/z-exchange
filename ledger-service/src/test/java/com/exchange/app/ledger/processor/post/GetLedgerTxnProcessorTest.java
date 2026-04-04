package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.dao.repository.LedgerEntryRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.common.utils.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
public class GetLedgerTxnProcessorTest {

    @MockBean
    private LedgerTxnRepository ledgerTxnRepository;

    @MockBean
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private GetLedgerTxnProcessor getLedgerTxnProcessor;

    @Test
    void shouldReturnInvalidParameterErrorWhenLookupValueIsEmpty() {
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, null, false);

        assertFalse(result.success());
        assertEquals(ErrorCode.INVALID_REQUEST_PARAMETER, result.errorCode());
        assertEquals("lookup_value is required", result.errorDetail());
    }

    @Test
    void shouldReturnInvalidParameterErrorWhenLookupTypeIsNull() {
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(null, "testValue", false);

        assertFalse(result.success());
        assertEquals(ErrorCode.INVALID_REQUEST_PARAMETER, result.errorCode());
        assertEquals("lookup_type is required", result.errorDetail());
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByTxnId() {
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", false);

        assertFalse(result.success());
        assertEquals(ErrorCode.LEDGER_NOT_FOUND, result.errorCode());
        assertEquals("ledger not found", result.errorDetail());
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByRefId() {
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertFalse(result.success());
        assertEquals(ErrorCode.LEDGER_NOT_FOUND, result.errorCode());
        assertEquals("ledger not found", result.errorDetail());
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByTxnIdWithoutEntries() {
        String id = "testTxnId";
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setTxnId(id);
        when(ledgerTxnRepository.getByTxnId(id)).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, id, false);

        assertTrue(result.success());
        assertEquals(mockTxn, result.value().txn);
        assertNull(result.value().entries);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByRefIdWithoutEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setReferenceId("testRefId");
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertTrue(result.success());
        assertEquals(mockTxn, result.value().txn);
        assertNull(result.value().entries);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundWithEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        LedgerEntry mockEntry = new LedgerEntry();
        String id = "testTxnId";
        mockTxn.setTxnId("testTxnId");
        when(ledgerTxnRepository.getByTxnId(id)).thenReturn(mockTxn);
        when(ledgerEntryRepository.getByTransactionId(id)).thenReturn(Collections.singletonList(mockEntry));

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, id, true);

        assertTrue(result.success());
        assertEquals(mockTxn, result.value().txn);
        assertEquals(1, result.value().entries.size());
        assertEquals(mockEntry, result.value().entries.get(0));
    }

    @Test
    void shouldReturnInternalErrorWhenEntriesNotFoundForTxnId() {
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setTxnId("testTxnId");
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(mockTxn);
        when(ledgerEntryRepository.getByTransactionId("testTxnId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", true);

        assertFalse(result.success());
        assertEquals(ErrorCode.INTERNAL_ERROR, result.errorCode());
        assertEquals("internal error", result.errorDetail());
    }
}