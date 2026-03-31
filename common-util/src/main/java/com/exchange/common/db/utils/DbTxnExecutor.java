package com.exchange.common.db.utils;

import com.exchange.common.utils.result.Result;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
@AllArgsConstructor
public class DbTxnExecutor {
    TransactionTemplate template;

    public <T extends Result<?>> T executeWithDefault(Supplier<T> supplier) {
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
