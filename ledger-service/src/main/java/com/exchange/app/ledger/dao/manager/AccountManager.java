package com.exchange.app.ledger.dao.manager;

import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.result.ErrorCode;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.result.Result;
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

    public Result<ErrorCode> insertWithDuplicateException(Account account) {
        int success;
        try {
            success = accountMapper.insert(account);
            if (success == 0) {
                return Result.fail(ErrorCode.ACCOUNT_DUPLICATED, account.toString());
            }
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                return Result.fail(ErrorCode.ACCOUNT_DUPLICATED, account.toString());
            } else {
                throw e;
            }
        }
        return Result.success();
    }
}
