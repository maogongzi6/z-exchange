package com.exchange.app.ledger.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    public LedgerTxn findByRefId(String referenceId) {
        var query = Wrappers.<LedgerTxn>lambdaQuery().eq(LedgerTxn::getReferenceId, referenceId);
        return mapper.selectOne(query);
    }
}
