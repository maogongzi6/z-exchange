package com.exchange.common.db.register;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.exchange.common.db.handler.AutofillMetaObjectHandler;
import com.exchange.common.db.utils.DbTxnExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

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
    public DbTxnExecutor dbTxnExecutor(TransactionTemplate template) {
        return new DbTxnExecutor(template);
    }
}
