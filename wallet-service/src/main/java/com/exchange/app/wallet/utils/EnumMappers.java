package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.common.enums.EnumMapper;
import com.exchange.proto.wallet.common.OwnerTypePb;
import com.exchange.proto.wallet.common.ServiceIdPb;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class EnumMappers {
    final static public EnumMapper<OwnerTypePb, OwnerType> ownerTypePbMapper = new EnumMapper<>(
        new HashMap<>() {{
            put(OwnerTypePb.OwnerTypePb_Unknown, OwnerType.UNKNOWN);
            put(OwnerTypePb.OwnerTypePb_System, OwnerType.SYSTEM);
            put(OwnerTypePb.OwnerTypePb_User, OwnerType.USER);
        }});

    final static public EnumMapper<ServiceIdPb, ServiceId> serviceIdPbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(ServiceIdPb.ServiceIdPb_Unknown, ServiceId.UNKNOWN);
                put(ServiceIdPb.ServiceIdPb_System, ServiceId.SYSTEM);
                put(ServiceIdPb.ServiceIdPb_User, ServiceId.USER);
            }}
    );
//
//    final static private Map<OwnerTypePb, OwnerType> ownerTypeMapper = new EnumMap<>(OwnerTypePb.class) {{
//        put(OwnerTypePb.OwnerTypePb_Unknown, OwnerType.UNKNOWN);
//        put(OwnerTypePb.OwnerTypePb_System, OwnerType.SYSTEM);
//        put(OwnerTypePb.OwnerTypePb_User, OwnerType.USER);
//    }};
//
//    static public OwnerType ownerTypePbToPo(OwnerTypePb pb) {
//        return ownerTypeMapper.get(pb);
//    }
//
//    final static private Map<ServiceIdPb, ServiceId> serviceIdMapper = new EnumMap<>(ServiceIdPb.class) {{
//        put(ServiceIdPb.ServiceIdPb_Unknown, ServiceId.UNKNOWN);
//        put(ServiceIdPb.ServiceIdPb_System, ServiceId.SYSTEM);
//        put(ServiceIdPb.ServiceIdPb_User, ServiceId.USER);
//    }};
//
//    static public ServiceId serviceIdPbToPo(ServiceIdPb pb) {
//        return serviceIdMapper.get(pb);
//    }
}
