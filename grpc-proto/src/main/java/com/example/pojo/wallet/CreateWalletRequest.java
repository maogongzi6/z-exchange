package com.example.pojo.wallet;

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
public class CreateWalletRequest implements Serializable {
    private ServiceIdPb serviceId;
    private String referenceId;
    private String assetId;
    private OwnerTypePb ownerType;
    private String ownerId;

    private static final long serialVersionUID = 1L;
}
