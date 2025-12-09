package com.exchange.app.ledger.dao.manager;

import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccountManager extends DbBaseManager<Account, AccountMapper> {
    @Autowired
    public AccountManager(AccountMapper mapper) {
        super(mapper);
    }
//    public int insertIgnore(Account account) {
//        try {
//            return accountMapper.insert(account);
//        } catch (Exception e) {
//            if (e instanceof DuplicateKeyException) {
//                return 0;
//            } else {
//                throw e;
//            }
//        }
//    }
}
