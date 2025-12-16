package com.exchange.app.wallet.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.po.wallet.BalanceSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BalanceSnapshotMapper extends BaseMapper<BalanceSnapshot> {
//    @Select("SELECT id, wallet_id FROM balance_snapshots WHERE wallet_id in #{wallet_ids} ORDER BY id")
//    List<BalanceSnapshot> selectOrderedIdByWalletIds(@Param("wallet_ids") List<String> walletIds);

//    @Update("UPDATE balance_snapshots SET available=available-#{amount}, reserved=reserved+#{amount} WHERE wallet_id=#{wallet_id} AND asset_id=#{asset_id} AND wallet_status=#{wallet_status} AND available-#{amount}>=0")
//    int updateReserveAmountByWalletId(@Param("wallet_id") String walletId, @Param("asset_id") String assetId,
//                                      @Param("wallet_status") WalletStatus status, @Param("amount") Long amount);
}
