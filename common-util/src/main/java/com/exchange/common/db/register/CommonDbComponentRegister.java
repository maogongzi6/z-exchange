package com.exchange.common.db.register;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.exchange.common.db.aop.DbExceptionTranslateAop;
import com.exchange.common.db.exception.DbExceptionTranslator;
import com.exchange.common.db.handler.AutofillMetaObjectHandler;
import com.exchange.common.db.utils.DbTxnExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CommonDbComponentRegister {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public AutofillMetaObjectHandler autofillMetaObjectHandler() {
        return new AutofillMetaObjectHandler();
    }

    @Bean
    public DbTxnExecutor dbTxnExecutor(PlatformTransactionManager transactionManager) {
        return new DbTxnExecutor(transactionManager);
    }

    @Bean
    public DbExceptionTranslator dbExceptionTranslator() {
        return new DbExceptionTranslator();
    }

    @Bean
    public DbExceptionTranslateAop dbExceptionTranslateAop(DbExceptionTranslator translator) {
        return new DbExceptionTranslateAop(translator);
    }
}
