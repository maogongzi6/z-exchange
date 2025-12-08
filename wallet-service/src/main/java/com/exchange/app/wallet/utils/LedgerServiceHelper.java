package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.proto.ledger.common.OwnerTypePb;

import java.util.EnumMap;
import java.util.Map;

public class LedgerServiceHelper {
    static private final Map<OwnerType, OwnerTypePb> ownerTypeToLedgerPbMapper = new EnumMap<>(OwnerType.class) {{
        put(OwnerType.UNKNOWN, OwnerTypePb.OwnerTypePb_Unknown);
        put(OwnerType.SYSTEM, OwnerTypePb.OwnerTypePb_System);
        put(OwnerType.USER, OwnerTypePb.OwnerTypePb_User);
    }};

    static public OwnerTypePb ownerTypeToLedgerPb(OwnerType po) {
        return ownerTypeToLedgerPbMapper.get(po);
    }
}
