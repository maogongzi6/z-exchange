package com.exchange.app.wallet.dao.manager;

import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BalanceSnapshotManager extends DbBaseManager<BalanceSnapshot, BalanceSnapshotMapper> {
    @Autowired
    public BalanceSnapshotManager(BalanceSnapshotMapper mapper) {
        super(mapper);
    }
//    public int insertIgnore(BalanceSnapshot snapshot) {
//        try {
//            return balanceSnapshotMapper.insert(snapshot);
//        } catch (Exception e) {
//            if (e instanceof DuplicateKeyException) {
//                return 0;
//            }
//            throw e;
//        }
//    }
}
