package com.exchange.common.db.utils;

import com.exchange.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DbTxnExecutor {
    private final PlatformTransactionManager transactionManager;

    public <T extends Result<?>> T executeWithDefault(Supplier<T> supplier) {
        TransactionTemplate txnTemplate = new TransactionTemplate(transactionManager);
        return txnTemplate.execute((transactionStatus) -> {
            try {
                T result = supplier.get();
                if (result.isFailed()) {
                    log.error("execute failed with result:{}", result);
                    transactionStatus.setRollbackOnly();
                }
                return result;
            } catch (Throwable e) {
                log.error("execute failed with exception, ", e);
                transactionStatus.setRollbackOnly();
                throw e;
            }
        });
    }

}
