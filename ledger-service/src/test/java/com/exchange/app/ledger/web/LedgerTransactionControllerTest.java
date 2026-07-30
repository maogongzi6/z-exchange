package com.exchange.app.ledger.web;

import com.exchange.app.ledger.metrics.LedgerBusinessMetrics;
import com.exchange.app.ledger.metrics.LedgerMetricTagValues;
import com.exchange.app.ledger.metrics.LedgerMetrics;
import com.exchange.app.ledger.metrics.LedgerMetricTags;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.processor.post.GetLedgerTxnProcessor;
import com.exchange.app.ledger.result.LedgerServiceErrorCode;
import com.exchange.common.result.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LedgerTransactionControllerTest {
    private GetLedgerTxnProcessor processor;
    private SimpleMeterRegistry meterRegistry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        processor = mock(GetLedgerTxnProcessor.class);
        meterRegistry = new SimpleMeterRegistry();
        mockMvc = standaloneSetup(new LedgerTransactionController(
                processor,
                new LedgerBusinessMetrics(meterRegistry)
        )).build();
    }

    @Test
    void shouldQueryByLedgerTxnIdAndRecordHttpBusinessMetrics() throws Exception {
        LedgerTxn txn = LedgerTxn.create("txn-1", "ref-1", "{\"source\":\"test\"}");
        LedgerEntry entry = LedgerEntry.create(
                "entry-1", "txn-1", "asset-1", "account-1", 100, Direction.DEBIT
        );
        when(processor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "txn-1", true))
                .thenReturn(Result.success(new GetLedgerTxnProcessor.LedgerTxnInfo(txn, List.of(entry))));

        mockMvc.perform(get("/api/v1/ledger/transactions/txn-1")
                        .queryParam("includeEntries", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.ledgerTxnId").value("txn-1"))
                .andExpect(jsonPath("$.transaction.referenceId").value("ref-1"))
                .andExpect(jsonPath("$.entries[0].ledgerEntryId").value("entry-1"))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"));

        double requestCount = meterRegistry.find(LedgerMetrics.LEDGER_REQUESTS.name())
                .tags(
                        LedgerMetricTags.OPERATION, LedgerMetricTagValues.Operations.GET_LEDGER_TXN_BY_ID,
                        LedgerMetricTags.INGRESS, LedgerMetricTagValues.Ingress.HTTP,
                        LedgerMetricTags.OUTCOME, LedgerMetricTagValues.Outcomes.SUCCESS,
                        LedgerMetricTags.ERROR_CODE, LedgerMetricTagValues.ErrorCodes.OK
                )
                .counter()
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(1.0, requestCount);
    }

    @Test
    void shouldQueryByReferenceIdWithoutEntries() throws Exception {
        LedgerTxn txn = LedgerTxn.create("txn-2", "ref-2", "{}");
        when(processor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.REF_ID, "ref-2", false))
                .thenReturn(Result.success(new GetLedgerTxnProcessor.LedgerTxnInfo(txn, null)));

        mockMvc.perform(get("/api/v1/ledger/transactions")
                        .queryParam("referenceId", "ref-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.ledgerTxnId").value("txn-2"))
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    void shouldReturnRestNotFoundError() throws Exception {
        when(processor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "missing", false))
                .thenReturn(Result.failure(LedgerServiceErrorCode.LEDGER_NOT_FOUND, "ledger not found"));

        mockMvc.perform(get("/api/v1/ledger/transactions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.namespace").value("ledger"))
                .andExpect(jsonPath("$.error.code").value(1001))
                .andExpect(jsonPath("$.error.message").value("ledger_not_found"))
                .andExpect(jsonPath("$.error.detail").value("ledger not found"));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        when(processor.getLedgerTxn(GetLedgerTxnProcessor.LookupType.TXN_ID, "txn-3", false))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(get("/api/v1/ledger/transactions/txn-3"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("server_error"))
                .andExpect(jsonPath("$.error.detail").doesNotExist());
    }
}
