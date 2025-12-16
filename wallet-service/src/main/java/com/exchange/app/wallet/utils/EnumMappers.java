package com.exchange.app.wallet.utils;

import com.exchange.app.wallet.po.enums.transaction.ActionType;
import com.exchange.app.wallet.po.enums.BusinessType;
import com.exchange.app.wallet.po.enums.OwnerType;
import com.exchange.app.wallet.po.enums.ServiceId;
import com.exchange.app.wallet.po.enums.transaction.TransactionStatus;
import com.exchange.common.enums.EnumMapper;
import com.exchange.proto.wallet.common.*;

import java.util.HashMap;

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

    final static public EnumMapper<BusinessTypePb, BusinessType> businessTypePbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(BusinessTypePb.BusinessTypePb_Unknown, BusinessType.UNKNOWN);
                put(BusinessTypePb.BusinessTypePb_Transfer, BusinessType.TRANSFER);
            }}
    );

    final static public EnumMapper<TransactionStatusPb, TransactionStatus> transactionStatusPbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(TransactionStatusPb.TransactionStatusPb_Unknown, TransactionStatus.UNKNOWN);
                put(TransactionStatusPb.TransactionStatusPb_Pending, TransactionStatus.PENDING);
                put(TransactionStatusPb.TransactionStatusPb_Completed, TransactionStatus.COMPLETED);
                put(TransactionStatusPb.TransactionStatusPb_Closed, TransactionStatus.CLOSED);
            }}
    );

    final static public EnumMapper<ActionTypePb, ActionType> actionTypePbMapper = new EnumMapper<>(
            new HashMap<>() {{
                put(ActionTypePb.ActionTypePb_Unknown, ActionType.UNKNOWN);
                put(ActionTypePb.ActionTypePb_Reserve, ActionType.RESERVE);
                put(ActionTypePb.ActionTypePb_TransferOut, ActionType.TRANSFER_OUT);
                put(ActionTypePb.ActionTypePb_TransferIn, ActionType.TRANSFER_IN);
            }}
    );
}
