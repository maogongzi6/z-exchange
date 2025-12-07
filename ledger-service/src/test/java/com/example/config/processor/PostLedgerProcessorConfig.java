//package com.example.config.com.example.processor;
//
//import com.example.com.example.mapper.AccountMapper;
//import com.example.com.example.mapper.LedgerEntryMapper;
//import com.example.com.example.mapper.LedgerTxnMapper;
//import com.example.com.example.processor.post.PostLedgerProcessor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.transaction.support.TransactionTemplate;
//
//@Configuration
//public class PostLedgerProcessorConfig {
//    @Autowired
//    private LedgerEntryMapper ledgerEntryMapper;
//    @Autowired
//    private LedgerTxnMapper ledgerTxnMapper;
//    @Autowired
//    private AccountMapper accountMapper;
//    @Autowired
//    private TransactionTemplate transactionTemplate;
//
//
//    @Bean
//    public PostLedgerProcessor postLedgerProcessor() {
//        return new PostLedgerProcessor(ledgerEntryMapper, ledgerTxnMapper, accountMapper, transactionTemplate);
//    }
//}
