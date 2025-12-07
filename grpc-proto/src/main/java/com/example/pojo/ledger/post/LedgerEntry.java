package com.example.pojo.ledger.post;

import com.example.proto.ledger.common.LedgerDirectionPb;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry implements Serializable {
    private String accountId;
    private LedgerDirectionPb direction;
    private long amount;
    private String assetId;

    private static final long serialVersionUID = 1L;

}
