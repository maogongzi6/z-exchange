package com.exchange.app.ledger.dao.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exchange.app.ledger.dao.mapper.AccountMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.enums.ServiceId;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AccountManager extends DbBaseManager<Account, AccountMapper> {
    @Autowired
    public AccountManager(AccountMapper mapper) {
        super(mapper);
    }

    public List<Account> getAccountIdInRef(List<String> refs) {
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Account::getAccountId, Account::getReferenceId).in(Account::getReferenceId, refs);
        return mapper.selectList(wrapper);
    }
}
