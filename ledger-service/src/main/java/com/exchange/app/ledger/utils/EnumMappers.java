package com.exchange.app.ledger.utils;

import com.exchange.app.ledger.po.enums.AccountCategory;
import com.exchange.app.ledger.po.enums.NormalSide;
import com.exchange.app.ledger.po.enums.OwnerType;
import com.exchange.app.ledger.po.enums.ServiceId;
import com.exchange.app.ledger.po.enums.Direction;
import com.exchange.common.enums.EnumMapper;
import com.exchange.proto.ledger.common.*;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class EnumMappers {
    final static public EnumMapper<LedgerDirectionPb, Direction> directionPbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(LedgerDirectionPb.LedgerDirection_Unknown, Direction.UNKNOWN);
                put(LedgerDirectionPb.LedgerDirection_Debit, Direction.DEBIT);
                put(LedgerDirectionPb.LedgerDirection_Credit, Direction.CREDIT);
            }}
    );

    final static public EnumMapper<AccountCategoryPb, AccountCategory> accountCategoryPbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(AccountCategoryPb.AccountCategory_Unknown, AccountCategory.UNKNOWN);
                put(AccountCategoryPb.AccountCategory_Asset, AccountCategory.ASSET);
                put(AccountCategoryPb.AccountCategory_Liability, AccountCategory.LIABILITY);
            }}
    );

    final static public EnumMapper<NormalSidePb, NormalSide> normalSidePbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(NormalSidePb.NormalSidePb_Unknown, NormalSide.UNKNOWN);
                put(NormalSidePb.NormalSidePb_Debit, NormalSide.DEBIT);
                put(NormalSidePb.NormalSidePb_Credit, NormalSide.CREDIT);
            }}
    );

    final static public EnumMapper<OwnerTypePb, OwnerType> ownerTypePbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(OwnerTypePb.OwnerTypePb_Unknown, OwnerType.UNKNOWN);
                put(OwnerTypePb.OwnerTypePb_System, OwnerType.SYSTEM);
                put(OwnerTypePb.OwnerTypePb_User, OwnerType.USER);
            }}
    );

    final static public EnumMapper<ServiceIdPb, ServiceId> serviceIdPbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(ServiceIdPb.ServiceIdPb_Unknown, ServiceId.UNKNOWN);
                put(ServiceIdPb.ServiceIdPb_System, ServiceId.SYSTEM);
                put(ServiceIdPb.ServiceIdPb_Wallet, ServiceId.WALLET);
            }}
    );

//    final static private Map<LedgerDirectionPb, Direction> directionPbToPoMapper = new EnumMap<>(LedgerDirectionPb.class) {{
//        put(LedgerDirectionPb.LedgerDirection_Unknown, Direction.UNKNOWN);
//        put(LedgerDirectionPb.LedgerDirection_Debit, Direction.DEBIT);
//        put(LedgerDirectionPb.LedgerDirection_Credit, Direction.CREDIT);
//    }};
//
//    static public Direction directionPbToPo(LedgerDirectionPb pb) {
//        return directionPbToPoMapper.get(pb);
//    }
//
//    final static private Map<AccountCategoryPb, AccountCategory> accountCategoryPbToPoMapper = new EnumMap<>(AccountCategoryPb.class) {{
//        put(AccountCategoryPb.AccountCategory_Unknown, AccountCategory.UNKNOWN);
//        put(AccountCategoryPb.AccountCategory_Asset, AccountCategory.ASSET);
//        put(AccountCategoryPb.AccountCategory_Liability, AccountCategory.LIABILITY);
//    }};
//
//    static public AccountCategory accountCategoryPbToPo(AccountCategoryPb pb) {
//        return accountCategoryPbToPoMapper.get(pb);
//    }
//
//    final static private Map<NormalSidePb, NormalSide> normalSideMapper = new EnumMap<>(NormalSidePb.class) {{
//        put(NormalSidePb.NormalSidePb_Unknown, NormalSide.UNKNOWN);
//        put(NormalSidePb.NormalSidePb_Debit, NormalSide.DEBIT);
//        put(NormalSidePb.NormalSidePb_Credit, NormalSide.CREDIT);
//    }};
//
//    static public NormalSide normalSidePbToPo(NormalSidePb pb) {
//        return normalSideMapper.get(pb);
//    }
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
//        put(ServiceIdPb.ServiceIdPb_Wallet, ServiceId.WALLET);
//    }};
//
//    static public ServiceId serviceIdPbToPo(ServiceIdPb pb) {
//        return serviceIdMapper.get(pb);
//    }

//    static public LedgerDirection poToPojo(Direction com.example.po) {
//        return poToPojoMapper.get(com.example.po);
//    }

    //    final static private Map<Direction, LedgerDirection> poToPojoMapper = new EnumMap<>(Direction.class) {{
//        put(Direction.UNKNOWN, LedgerDirection.UNKNOWN);
//        put(Direction.DEBIT, LedgerDirection.DEBIT);
//        put(Direction.CREDIT, LedgerDirection.CREDIT);
//    }};
}
