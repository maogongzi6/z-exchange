package com.exchange.app.ledger.dao.repository;

import com.exchange.app.ledger.dao.mapper.LedgerTxnMapper;
import com.exchange.app.ledger.po.ledger.LedgerTxn;
import com.exchange.common.db.manager.DbBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LedgerTxnRepository extends DbBaseRepository<LedgerTxn, LedgerTxnMapper> {
    @Autowired
    public LedgerTxnRepository(LedgerTxnMapper mapper) {
        super(mapper);
    }
}
