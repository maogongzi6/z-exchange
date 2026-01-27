package com.exchange.common.db.utils;

import com.exchange.common.utils.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
public class DbTransactionHelper {
    public static <T extends Result<?>> T executeWithResult(TransactionTemplate template, int propagation, Supplier<T> supplier) {
        template.setPropagationBehavior(propagation);
        return template.execute((transactionStatus) -> {
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
