package com.example.dao.manager;

import com.example.dao.mapper.BalanceSnapshotMapper;
import com.example.dao.mapper.WalletAccountMappingMapper;
import com.example.exception.WalletException;
import com.example.po.wallet.BalanceSnapshot;
import com.example.po.wallet.WalletAccountMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WalletAccountMappingManager {
    final private WalletAccountMappingMapper walletAccountMappingMapper;

    public void insertWithDuplicateException(WalletAccountMapping mapping) {
        try {
            int success = walletAccountMappingMapper.insert(mapping);
            if (success == 0) {
                throw WalletException.walletDuplicated("mapping_duplicated" + mapping);
            }
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                throw (WalletException) WalletException.walletDuplicated("mapping_duplicated" + mapping).initCause(e);
            }
            throw e;

        }
    }
}
