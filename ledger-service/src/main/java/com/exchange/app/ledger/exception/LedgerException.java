package com.exchange.app.ledger.exception;

public class LedgerException extends CustomException {
    private LedgerException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

//    static public LedgerException invalidLedgerDirectionException(String message) {
//        return new LedgerException(ErrorCode.INVALID_LEDGER_DIRECTION, message);
//    }
//
//    static public LedgerException imbalancedLedgerTxnException(String message) {
//        return new LedgerException(ErrorCode.IMBALANCED_LEDGER_TXN, message);
//    }

    static public LedgerException duplicatedLedger(String message) {
        return new LedgerException(ErrorCode.LEDGER_DUPLICATED, message);
    }
}
