package com.exchange.app.wallet.dao.manager;

import com.exchange.app.wallet.dao.mapper.BalanceSnapshotMapper;
import com.exchange.app.wallet.exception.WalletException;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BalanceSnapshotManager {
    final private BalanceSnapshotMapper balanceSnapshotMapper;

    public void insertWithDuplicateException(BalanceSnapshot snapshot) {
        int success;
        try {
            success = balanceSnapshotMapper.insert(snapshot);
            if (success == 0) {
                throw WalletException.walletDuplicated("snapshot_duplicated" + snapshot);
            }
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                throw (WalletException) WalletException.walletDuplicated("snapshot_duplicated" + snapshot).initCause(e);
            }
            throw e;

        }
    }
}
