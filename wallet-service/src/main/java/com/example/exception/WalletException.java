package com.example.exception;

public class WalletException extends CustomException {
    private WalletException(ErrorCode errorCode) {super(errorCode);}
    private WalletException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    static public WalletException walletNotFound(String message) {
        return new WalletException(ErrorCode.WALLET_NOT_FOUND, message);
    }

    static public WalletException walletDuplicated(String message) {
        return new WalletException(ErrorCode.WALLET_DUPLICATED, message);
    }

    static public WalletException createAccountFailed() {
        return new WalletException(ErrorCode.CREATE_ACCOUNT_FAILED);
    }

    static public WalletException enableWalletFailed(String message) {
        return new WalletException(ErrorCode.ENABLE_WALLET_FAILED, message);
    }

    static public WalletException balanceSnapshotDuplicated(String message) {
        return new WalletException(ErrorCode.BALANCE_SNAPSHOT_DUPLICATED, message);
    }

    static  public WalletException walletAccountMappingDuplicated(String message) {
        return new WalletException(ErrorCode.WALLET_ACCOUNT_MAPPING_DUPLICATED, message);
    }
}
