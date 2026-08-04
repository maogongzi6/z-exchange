package com.exchange.common.db.utils;

import com.exchange.common.exception.DbException;
import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DefaultDbTxnExecutor implements DbTxnExecutor {
    private final PlatformTransactionManager transactionManager;

    @Override
    public <T extends Result<?>> T executeWithDefault(Supplier<T> supplier) {
        TransactionTemplate txnTemplate = new TransactionTemplate(transactionManager);
        return txnTemplate.execute((transactionStatus) -> {
            try {
                T value = supplier.get();
                if (value.isFailed()) {
                    log.error("execute failed with result:{}", value);
                    transactionStatus.setRollbackOnly();
                }
                return value;
            } catch (Throwable e) {
                if (!(e instanceof DbException)) {
                    log.error("execute failed with exception, ", e);
                }
                transactionStatus.setRollbackOnly();
                throw e;
            }
        });
    }
}
