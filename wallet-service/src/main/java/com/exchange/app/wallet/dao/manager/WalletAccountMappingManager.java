package com.exchange.app.wallet.dao.manager;

import com.exchange.app.wallet.dao.mapper.WalletAccountMappingMapper;
import com.exchange.app.wallet.po.wallet.WalletAccountMapping;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WalletAccountMappingManager extends DbBaseManager<WalletAccountMapping, WalletAccountMappingMapper> {
    @Autowired
    public WalletAccountMappingManager(WalletAccountMappingMapper mapper) {
        super(mapper);
    }

//    public int insertIgnore(WalletAccountMapping mapping) {
//        try {
//            return walletAccountMappingMapper.insert(mapping);
//        } catch (Exception e) {
//            if (e instanceof DuplicateKeyException) {
//               return 0;
//            }
//            throw e;
//        }
//    }
}
