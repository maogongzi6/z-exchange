package com.exchange.app.wallet.dao.manager;

import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.po.wallet.Wallet;
import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Result;
import com.exchange.common.db.DbBaseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WalletManager extends DbBaseManager<Wallet, WalletMapper> {
    @Autowired
    public WalletManager(WalletMapper mapper) {
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
