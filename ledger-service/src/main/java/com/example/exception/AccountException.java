package com.example.exception;

public class AccountException extends CustomException {
    private AccountException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    static public AccountException accountNotFound(String message) {
        return new AccountException(ErrorCode.ACCOUNT_NOT_FOUND, message);
    }

    static public AccountException accountDuplicated(String message) {
        return new AccountException(ErrorCode.ACCOUNT_DUPLICATED, message);
    }
}
