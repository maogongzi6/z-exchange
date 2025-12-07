package com.example.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.po.ledger.LedgerEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LedgerEntryMapper extends BaseMapper<LedgerEntry> {
    @Insert("<script>" +
            "INSERT INTO ledger_entries (entry_id, txn_id, asset_id, account_id, amount, direction) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.entryId}, #{item.txnId}, #{item.assetId}, #{item.accountId}, #{item.amount}, #{item.direction})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<LedgerEntry> list);
}
