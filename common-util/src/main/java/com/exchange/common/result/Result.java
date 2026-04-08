package com.exchange.common.result;

import com.exchange.common.result.error.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
public final class Result<T> implements IResult<T> {
    private final boolean success;
    private final T value;
    private final ErrorCode errorCode;
    private final String detail;
    private final String scope;

    private Result(boolean success, T value, ErrorCode errorCode, String detail, String scope) {
        if (success && errorCode != null) {
            throw new IllegalArgumentException("success result should not have errorCode");
        }
        if (!success && errorCode == null) {
            throw new IllegalArgumentException("failed result must have errorCode");
        }
        if (!success && value != null) {
            throw new IllegalArgumentException("failed result should not have value");
        }
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.detail = detail;
        this.scope = scope;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T value) {
        return success(value, null, null);
    }

    public static <T> Result<T> success(T value, String detail) {
        return success(value, detail, null);
    }

    public static <T> Result<T> success(T value, String detail, String scope) {
        return new Result<>(true, value, null, detail, scope);
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return failure(errorCode, null, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String detail) {
        return failure(errorCode, detail, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String detail, String scope) {
        return new Result<>(false, null, Objects.requireNonNull(errorCode, "errorCode"), detail, scope);
    }

    public static <T> Result<T> from(IResult<T> result) {
        Objects.requireNonNull(result, "result");
        if (result.isSuccess()) {
            return success(result.getValue(), result.getDetail(), result.getScope());
        }
        return failure(result.getErrorCode(), result.getDetail(), result.getScope());
    }

    public static <T> Result<T> from(T value, IResult<?> result) {
        Objects.requireNonNull(result, "result");
        if (result.isSuccess()) {
            return success(value, result.getDetail(), result.getScope());
        }
        return failure(result.getErrorCode(), result.getDetail(), result.getScope());
    }

//    @Override
//    public boolean isSuccess() {
//        return success;
//    }
//
//    @Override
//    public ErrorCode getErrorCode() {
//        return errorCode;
//    }
//
//    @Override
//    public T getValue() {
//        return value;
//    }
//
//    @Override
//    public String getDetail() {
//        return detail;
//    }
//
//    @Override
//    public String getScope() {
//        return scope;
//    }

}
