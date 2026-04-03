package com.exchange.app.ledger.processor.post;

import com.exchange.app.ledger.dao.store.LedgerTxnStore;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.result.Results;
import com.exchange.common.utils.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.exchange.app.ledger.dao.repository.LedgerEntryRepository;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class GetLedgerTxnProcessor {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTxnStore ledgerTxnStore;

    public enum LookupType {
        TXN_ID, REF_ID
    }

    @RequiredArgsConstructor
    public static class LedgerTxnInfo {
        public final LedgerTxn txn;
        public final List<LedgerEntry> entries;
    }

    public Result<LedgerTxnInfo> getLedgerTxn(LookupType type, String lookupValue, boolean includeEntries) {
        // Step 1: Validate parameters
        if (Strings.isEmpty(lookupValue)) {
            log.error("lookup_value is required");
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "lookup_value is required");
        }
        if (type == null) {
            log.error("lookup_type is required");
            return Results.fail(ErrorCode.INVALID_REQUEST_PARAMETER, "lookup_type is required");
        }

        // Step 2: Query the ledger transaction
        LedgerTxn ledgerTxn;
        Result<LedgerTxn> ledgerResult = null;
        switch (type) {
            case TXN_ID:
                ledgerResult = ledgerTxnStore.getByTxnId(lookupValue);
                break;
            case REF_ID:
                ledgerResult = ledgerTxnStore.getByRefId(lookupValue);
                break;
            default:
                log.error("Invalid lookup type: {}", type);
                return Results.fail(ErrorCode.INVALID_ENUM_ERROR, "invalid lookup type: " + type);
        }
        if (!ledgerResult.success) {
            log.error("load ledger txn failed, {}: {}", type.name(), lookupValue);
            return Results.fail(ErrorCode.INTERNAL_ERROR, "load ledger txn failed");
        }
        ledgerTxn = ledgerResult.value;

        if (ledgerTxn == null) {
            log.error("Ledger not found for {}: {}", type, lookupValue);
            return Results.fail(ErrorCode.LEDGER_NOT_FOUND, "ledger not found");
        }

        // Step 3: Query entries if requested
        List<LedgerEntry> ledgerEntries = includeEntries
                ? ledgerEntryRepository.getByTransactionId(ledgerTxn.getTxnId())
                : null;

        if (includeEntries && CollectionUtils.isEmpty(ledgerEntries)) {
            log.error("internal data error, abnormal ledger, entry not found for {}: {}", type, lookupValue);
            throw new DataIntegrityViolationException(String.format("internal data error, abnormal ledger, entry not found, %s: %s", type, lookupValue));
        }

        // Step 4: Combine transaction and entries into a result object
        LedgerTxnInfo txnInfo = new LedgerTxnInfo(ledgerTxn, ledgerEntries);
        return Results.success(txnInfo);
    }
}
