package com.exchange.app.ledger.web.dto;

import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.app.ledger.processor.post.GetLedgerTxnProcessor;

import java.util.List;

/**
 * Public HTTP representation of a ledger transaction.
 *
 * <p>This DTO is deliberately independent of both persistence entities and protobuf messages so
 * the external HTTP contract can evolve without changing the internal gRPC contract.</p>
 */
public record LedgerTransactionResponse(
        Transaction transaction,
        List<Entry> entries
) {
    public static LedgerTransactionResponse from(GetLedgerTxnProcessor.LedgerTxnInfo info) {
        List<Entry> responseEntries = info.entries == null
                ? List.of()
                : info.entries.stream().map(Entry::from).toList();
        return new LedgerTransactionResponse(Transaction.from(info.txn), responseEntries);
    }

    public record Transaction(
            String ledgerTxnId,
            String referenceId,
            String metadata
    ) {
        private static Transaction from(LedgerTxn txn) {
            return new Transaction(txn.getTxnId(), txn.getReferenceId(), txn.getMetadata());
        }
    }

    public record Entry(
            String ledgerEntryId,
            String accountId,
            String direction,
            long amount,
            String assetId
    ) {
        private static Entry from(LedgerEntry entry) {
            return new Entry(
                    entry.getEntryId(),
                    entry.getAccountId(),
                    entry.getDirection().name(),
                    entry.getAmount(),
                    entry.getAssetId()
            );
        }
    }
}
