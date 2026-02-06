package com.exchange.app.wallet.dao.repository;

import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.po.wallet.Wallet;
import com.exchange.common.db.manager.DbBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WalletRepository extends DbBaseRepository<Wallet, WalletMapper> {
    @Autowired
    public WalletRepository(WalletMapper mapper) {
        super(mapper);
    }
//    public int insertIgnoreDuplicateError(Wallet wallet) {
//        try {
//            return walletMapper.insert(wallet);
//        } catch (Exception e) {
//            if (e instanceof DuplicateKeyException) {
//                return 0;
//            }
//            throw e;
//        }
//    }
}
