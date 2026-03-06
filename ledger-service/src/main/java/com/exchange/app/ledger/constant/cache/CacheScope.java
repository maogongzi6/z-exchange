package com.exchange.app.ledger.constant.cache;

public class CacheScope {
    // Cache scope for account-related operations
    final static public String ACCOUNT_CACHE = "account";

    // Cache scope for ledger transaction operations
    final static public String LEDGER_TXN_CACHE = "ledger";

    // Cache scope for ledger entry operations
    final static public String LEDGER_ENTRY_CACHE = "entry";

    // scope for ledger_txn_id -> ledger_txn
    final static public String LEDGER_TXN_ID_SCOPE = LEDGER_TXN_CACHE + ":id";

    // scope for ledger_ref_id -> txn_id
    final static public String LEDGER_REF_ID_SCOPE = LEDGER_TXN_CACHE + ":ref";

    // method to generate LedgerTxnId key
    public static String ledgerTxnIdKey(String txnId) {
        return LEDGER_TXN_ID_SCOPE + ":" + txnId;
    }

    // method to generate LedgerRefId key
    public static String ledgerRefIdKey(String refId) {
        return LEDGER_REF_ID_SCOPE + ":" + refId;
    }

}
