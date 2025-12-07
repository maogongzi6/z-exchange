package com.example.exception;

public class AssetException extends CustomException {
    private AssetException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    static public AssetException assetNotFound(String message) {
        return new AssetException(ErrorCode.ASSET_NOT_FOUND, message);
    }
}
