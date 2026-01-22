package com.exchange.app.ledger.dao.manager;

import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.db.manager.DbBaseManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnManager extends DbBaseManager<LedgerTxn, LedgerTxnMapper> {
    @Autowired
    public LedgerTxnManager(LedgerTxnMapper mapper) {
        super(mapper);
    }
}
