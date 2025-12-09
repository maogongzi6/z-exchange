package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.exception.DbException;
import com.exchange.app.ledger.result.Result;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;

public class DbTransactionHelper {
    public static <T extends Result<?>> T executeWithResult(TransactionTemplate template, int propagation, Callable<T> callable) {
        template.setPropagationBehavior(propagation);
        return template.execute((transactionStatus) -> {
            try {
                T result = callable.call();
                if (!Result.isSuccess(result)) {
                    transactionStatus.setRollbackOnly();
                }
                return result;
            } catch (Throwable e) {
                transactionStatus.setRollbackOnly();
                throw new DbException(e);
            }
        });
    }
}
