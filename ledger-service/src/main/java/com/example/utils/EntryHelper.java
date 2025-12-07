package com.example.utils;

import com.example.po.enums.Direction;
import com.example.po.ledger.LedgerEntry;

public class EntryHelper {
    static public LedgerEntry pojoToPo(String txnId, String entryId, com.example.pojo.ledger.post.LedgerEntry pojoEntry) {
        Direction direction = EnumConvertHelper.directionPbToPo(pojoEntry.getDirection());
        return LedgerEntry.create(entryId, txnId, pojoEntry.getAssetId(), pojoEntry.getAccountId(), pojoEntry.getAmount(), direction);
    }
}
