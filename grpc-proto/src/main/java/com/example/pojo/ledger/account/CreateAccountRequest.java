package com.example.pojo.ledger.account;

import com.example.proto.ledger.common.AccountCategoryPb;
import com.example.proto.ledger.common.NormalSidePb;
import com.example.proto.ledger.common.OwnerTypePb;
import com.example.proto.ledger.common.ServiceIdPb;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest implements Serializable {
    private String referenceId;
    private ServiceIdPb serviceId;
    private String  assetId;
    private AccountCategoryPb category;
    private NormalSidePb normalSide;
    private OwnerTypePb ownerType;
    private String ownerId;

    private static final long serialVersionUID = 1L;
}
