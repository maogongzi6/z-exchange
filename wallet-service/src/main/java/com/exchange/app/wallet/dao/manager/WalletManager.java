package com.exchange.app.wallet.dao.manager;

import com.exchange.app.wallet.dao.mapper.WalletMapper;
import com.exchange.app.wallet.exception.WalletException;
import com.exchange.app.wallet.po.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WalletManager {
    final private WalletMapper walletMapper;

    public void insertWithDuplicateException(Wallet wallet) {
        int success;
        try {
            success = walletMapper.insert(wallet);
            if (success == 0) {
                throw WalletException.walletDuplicated("wallet_duplicated" + wallet);
            }
        } catch (Exception e) {
            if (e instanceof DuplicateKeyException) {
                throw (WalletException) WalletException.walletDuplicated("wallet_duplicated" + wallet).initCause(e);
            }
            throw e;

        }
    }

//    public int updateWalletStatus(String walletId, WalletStatus oldStatus, WalletStatus newStatus) {
//        if (Strings.isEmpty(walletId)) {
//            throw ServerError.invalidDbParameter("empty_wallet_id");
//        }
//
//        LambdaUpdateWrapper<Wallet> updateWrapper = new LambdaUpdateWrapper<>();
//        updateWrapper.eq(Wallet::getWalletId, walletId).eq(Wallet::getWalletStatus, oldStatus).set(Wallet::getWalletStatus, newStatus);
//        return walletMapper.update(updateWrapper);
//    }
}
