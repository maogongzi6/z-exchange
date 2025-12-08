package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.app.ledger.po.ledger.LedgerEntry;
import com.exchange.proto.ledger.post.LedgerEntryPb;

public class EntryHelper {
    static public LedgerEntry protoToDto(String txnId, String entryId, LedgerEntryPb protoEntry) {
        Direction direction = EnumConvertHelper.directionPbToPo(protoEntry.getDirection());
        return LedgerEntry.create(entryId, txnId, protoEntry.getAssetId(), protoEntry.getAccountId(), protoEntry.getAmount(), direction);
    }
}
