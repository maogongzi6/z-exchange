package com.example.utils;

import com.example.po.enums.*;
import com.example.proto.ledger.common.*;

import java.util.EnumMap;
import java.util.Map;

public class EnumConvertHelper {

    final static private Map<OwnerTypePb, OwnerType> ownerTypeMapper = new EnumMap<>(OwnerTypePb.class) {{
        put(OwnerTypePb.OwnerTypePb_Unknown, OwnerType.UNKNOWN);
        put(OwnerTypePb.OwnerTypePb_System, OwnerType.SYSTEM);
        put(OwnerTypePb.OwnerTypePb_User, OwnerType.USER);
    }};

    static public OwnerType ownerTypePbToPo(OwnerTypePb pb) {
        return ownerTypeMapper.get(pb);
    }

    final static private Map<ServiceIdPb, ServiceId> serviceIdMapper = new EnumMap<>(ServiceIdPb.class) {{
        put(ServiceIdPb.ServiceIdPb_Unknown, ServiceId.UNKNOWN);
        put(ServiceIdPb.ServiceIdPb_System, ServiceId.SYSTEM);
        put(ServiceIdPb.ServiceIdPb_Wallet, ServiceId.USER);
    }};

    static public ServiceId serviceIdPbToPo(ServiceIdPb pb) {
        return serviceIdMapper.get(pb);
    }
}
