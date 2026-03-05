package com.exchange.app.ledger.dao.repository;

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

    public LedgerTxn getByTxnId(String txnId) {
        var query = Wrappers.<LedgerTxn>lambdaQuery().eq(LedgerTxn::getTxnId, txnId);
        return mapper.selectOne(query);
    }

    public LedgerTxn getByRefId(String referenceId) {
        var query = Wrappers.<LedgerTxn>lambdaQuery().eq(LedgerTxn::getReferenceId, referenceId);
        return mapper.selectOne(query);
    }

    // update version field in business code or re-get if needed
    public int updateMetadataById(LedgerTxn txn) {
        LedgerTxn toUpdate = new LedgerTxn() {{
            setId(txn.getId());
            setMetadata(txn.getMetadata());
            setVersion(txn.getVersion());
        }};
        return mapper.updateById(toUpdate);
    }
}
