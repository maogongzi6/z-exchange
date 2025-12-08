package com.exchange.app.wallet.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.WalletStatus;
import com.exchange.app.wallet.po.wallet.Wallet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WalletMapper extends BaseMapper<Wallet> {
    @Select("SELECT * FROM wallets WHERE wallet_id=#{wallet_id}")
    Wallet selectByWalletId(@Param("wallet_id") String walletId);
    @Select("SELECT * FROM wallets WHERE service_id=#{service_id} AND reference_id=#{reference_id}")
    Wallet selectByReferenceId(@Param("service_id") ServiceId serviceId, @Param("reference_id") String referenceId);

    @Update("UPDATE wallets SET wallet_status=#{new_status} WHERE wallet_id=#{wallet_id} and wallet_status=#{old_status}")
    int updateWalletStatus(@Param("wallet_id") String walletId, @Param("old_status") WalletStatus oldStatus, @Param("new_status") WalletStatus newStatus);
}
