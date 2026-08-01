package com.exchange.common.db.utils;

import com.exchange.common.result.Result;

import java.util.function.Supplier;

public interface DbTxnExecutor {
    <T extends Result<?>> T executeWithDefault(Supplier<T> supplier);
}
