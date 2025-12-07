package com.example.dao.manager;

import com.example.dao.mapper.AccountMapper;
import com.example.exception.AccountException;
import com.example.po.account.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AccountManager {
    final private AccountMapper accountMapper;

    public void insertWithDuplicateException(Account account) {
        int success;
        try {
            success = accountMapper.insert(account);
            if (success == 0) {
                throw AccountException.accountDuplicated(account.toString());
            }
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                throw (AccountException) AccountException.accountDuplicated(account.toString()).initCause(e);
            } else {
                throw e;
            }
        }
    }
}
