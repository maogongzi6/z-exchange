package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.dao.repository.LedgerEntryRepository;
import com.exchange.app.ledger.dao.repository.LedgerTxnRepository;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

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

        assertFalse(result.isSuccess());
        assertEquals(LedgerServiceErrorCode.INVALID_REQUEST_PARAMETER, result.getErrorCode());
        assertEquals("lookup_value is required", result.getDetail());
    }

    @Test
    void shouldReturnInvalidParameterErrorWhenLookupTypeIsNull() {
        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(null, "testValue", false);

        assertFalse(result.isSuccess());
        assertEquals(LedgerServiceErrorCode.INTERNAL_ERROR, result.getErrorCode());
        assertEquals("lookup_type is required", result.getDetail());
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByTxnId() {
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", false);

        assertFalse(result.isSuccess());
        assertEquals(LedgerServiceErrorCode.LEDGER_NOT_FOUND, result.getErrorCode());
        assertEquals("ledger not found", result.getDetail());
    }

    @Test
    void shouldReturnLedgerNotFoundErrorWhenLedgerNotFoundByRefId() {
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(null);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertFalse(result.isSuccess());
        assertEquals(LedgerServiceErrorCode.LEDGER_NOT_FOUND, result.getErrorCode());
        assertEquals("ledger not found", result.getDetail());
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByTxnIdWithoutEntries() {
        String id = "testTxnId";
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setTxnId(id);
        when(ledgerTxnRepository.getByTxnId(id)).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, id, false);

        assertTrue(result.isSuccess());
        assertEquals(mockTxn, result.getValue().txn);
        assertNull(result.getValue().entries);
    }

    @Test
    void shouldReturnSuccessWhenLedgerFoundByRefIdWithoutEntries() {
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setReferenceId("testRefId");
        when(ledgerTxnRepository.getByRefId("testRefId")).thenReturn(mockTxn);

        Result<GetLedgerTxnProcessor.LedgerTxnInfo> result = getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "testRefId", false);

        assertTrue(result.isSuccess());
        assertEquals(mockTxn, result.getValue().txn);
        assertNull(result.getValue().entries);
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

        assertTrue(result.isSuccess());
        assertEquals(mockTxn, result.getValue().txn);
        assertEquals(1, result.getValue().entries.size());
        assertEquals(mockEntry, result.getValue().entries.get(0));
    }

    @Test
    void shouldThrowWhenEntriesNotFoundForTxnId() {
        LedgerTxn mockTxn = new LedgerTxn();
        mockTxn.setTxnId("testTxnId");
        when(ledgerTxnRepository.getByTxnId("testTxnId")).thenReturn(mockTxn);
        when(ledgerEntryRepository.getByTransactionId("testTxnId")).thenReturn(null);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> getLedgerTxnProcessor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "testTxnId", true)
        );
    }
}
