package com.exchange.app.wallet.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.WalletServer;
import com.exchange.common.db.manager.DbBaseRepository;
import com.exchange.common.db.metrics.MybatisStatementMetricsInterceptor;
import com.exchange.common.db.register.CommonDbComponentRegister;
import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.exception.DbException;
import com.exchange.common.metrics.CommonMetricTags;
import com.exchange.common.metrics.CommonMetricTagValues;
import com.exchange.common.metrics.CommonMetrics;
import com.exchange.common.result.error.DbErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletDbExceptionTranslationConfigTest {
    @Test
    void walletServerEnablesAspectJProxying() {
        assertNotNull(WalletServer.class.getAnnotation(EnableAspectJAutoProxy.class));
    }

    @Test
    void commonDbWiringTranslatesRepositoryAndTransactionFailuresWithoutLosingMetrics() {
        try (var context = new AnnotationConfigApplicationContext(DbTestConfiguration.class)) {
            TestRepository repository = context.getBean(TestRepository.class);
            DbTxnExecutor txnExecutor = context.getBean(DbTxnExecutor.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            // These proxy checks prevent a configuration regression where the advice beans exist
            // but repository and transaction calls bypass them.
            assertTrue(AopUtils.isAopProxy(repository));
            assertTrue(AopUtils.isAopProxy(txnExecutor));
            assertNotNull(context.getBean(MybatisStatementMetricsInterceptor.class));

            DbException repositoryFailure = assertThrows(
                    DbException.class,
                    repository::throwConnectionFailure
            );
            assertEquals(DbErrorCode.DB_UNAVAILABLE, repositoryFailure.getDbErrorCode());

            DbException transactionFailure = assertThrows(
                    DbException.class,
                    () -> txnExecutor.executeWithDefault(() -> {
                        throw new CannotGetJdbcConnectionException("transaction connection unavailable");
                    })
            );
            assertEquals(DbErrorCode.DB_UNAVAILABLE, transactionFailure.getDbErrorCode());
            assertEquals(1, registry.find(CommonMetrics.DB_TRANSACTION_DURATION.name())
                    .tag(CommonMetricTags.OUTCOME, CommonMetricTagValues.Outcomes.ERROR)
                    .timer()
                    .count());

            DbException existing = DbException.timeout("existing", new RuntimeException("timeout"));
            assertSame(existing, assertThrows(DbException.class, () -> repository.rethrow(existing)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @Import(CommonDbComponentRegister.class)
    static class DbTestConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            // The test needs transaction lifecycle behavior, not a database connection.
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };
        }

        @Bean
        TestRepository testRepository() {
            return new TestRepository();
        }
    }

    static class TestRepository extends DbBaseRepository<Object, BaseMapper<Object>> {
        TestRepository() {
            super(null);
        }

        public Object throwConnectionFailure() {
            throw new CannotGetJdbcConnectionException("repository connection unavailable");
        }

        public Object rethrow(DbException exception) {
            throw exception;
        }
    }
}
