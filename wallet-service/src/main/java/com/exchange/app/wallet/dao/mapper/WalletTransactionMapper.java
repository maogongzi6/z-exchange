package com.exchange.app.wallet.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.app.wallet.po.enums.transaction.TransactionType;
import com.exchange.app.wallet.po.transaction.WalletTransaction;
import com.exchange.app.wallet.po.wallet.Wallet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WalletTransactionMapper extends BaseMapper<WalletTransaction> {
//    @Select("SELECT EXISTS(SELECT 1 FROM wallet_transactions WHERE initiator=#{initiator} AND idempotency_key=#{idempotency_key})")
//    Boolean existsByIdempotency(@Param("initiator")ServiceId serviceId, @Param("idempotency_key")String idempotencyKey);

//    @Select("SELECT * FROM wallet_transactions WHERE initiator=#{initiator} AND idempotency_key=#{idempotency_key}")
//    WalletTransaction selectByIdempotency(@Param("initiator")ServiceId serviceId, @Param("idempotency_key")String idempotencyKey);
//
//    @Select("SELECT * FROM wallet_transactions WHERE initiator=#{initiator} AND idempotency_key=#{idempotency_key} AND txn_status=#{status} AND txn_type=#{txn_type} AND business_type=#{business_type}")
//    WalletTransaction selectTxn(@Param("initiator")ServiceId serviceId, @Param("idempotency_key")String idempotencyKey,
//                                         @Param("txn_status")TransactionStatus status, @Param("txn_type")TransactionType txnType, @Param("business_type")BusinessType businessType);
}
