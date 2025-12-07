package com.example.pojo.ledger.post;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostTransactionRequest implements Serializable {
    private String legerTransactionId;
    private String referenceId;
    private String description;
    private List<LedgerEntry> entries;

    private static final long serialVersionUID = 1L;
}
