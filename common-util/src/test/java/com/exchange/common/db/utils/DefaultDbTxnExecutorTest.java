package com.exchange.common.db.utils;

import com.exchange.common.result.Result;
import com.exchange.common.result.error.DbErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDbTxnExecutorTest {
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;
    private DbTxnExecutor executor;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        executor = new DefaultDbTxnExecutor(transactionManager);
    }

    @Test
    void commitsSuccessfulResult() {
        Result<Void> result = executor.executeWithDefault(Result::success);

        assertEquals(Result.success(), result);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void marksFailedResultForRollback() {
        Result<Void> result = executor.executeWithDefault(
                () -> Result.failure(DbErrorCode.DB_INTERNAL));

        assertEquals(Result.failure(DbErrorCode.DB_INTERNAL), result);
        verify(transactionStatus).setRollbackOnly();
    }

    @Test
    void rollsBackThrownException() {
        assertThrows(IllegalStateException.class, () -> executor.executeWithDefault(() -> {
            throw new IllegalStateException("transaction failed");
        }));

        verify(transactionStatus).setRollbackOnly();
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void propagatesCommitFailure() {
        doThrow(new IllegalStateException("commit failed"))
                .when(transactionManager).commit(transactionStatus);

        assertThrows(IllegalStateException.class,
                () -> executor.executeWithDefault(Result::success));
    }
}
