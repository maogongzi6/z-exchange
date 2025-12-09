package com.exchange.app.ledger.dao.manager;

import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.account.Account;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnManager extends DbBaseManager<LedgerTxn, LedgerTxnMapper> {
    @Autowired
    public LedgerTxnManager(LedgerTxnMapper mapper) {
        super(mapper);
    }
}
