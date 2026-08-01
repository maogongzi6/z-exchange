package com.exchange.common.db.aop;

import com.exchange.common.db.utils.DbTxnExecutor;
import com.exchange.common.db.utils.DefaultDbTxnExecutor;
import com.exchange.common.db.utils.MeteredDbTxnExecutor;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DbExceptionTranslateAopPointcutTest {
    @Test
    void transactionPointcutMatchesAllExecutorImplementations() throws NoSuchMethodException {
        Method advice = DbExceptionTranslateAop.class.getMethod(
                "translateTxnException",
                org.aspectj.lang.ProceedingJoinPoint.class
        );
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(advice.getAnnotation(Around.class).value());

        Method execute = DbTxnExecutor.class.getMethod("executeWithDefault", Supplier.class);

        assertTrue(pointcut.matches(execute, DefaultDbTxnExecutor.class));
        assertTrue(pointcut.matches(execute, MeteredDbTxnExecutor.class));
    }
}
