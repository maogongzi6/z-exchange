package com.example.pojo.ledger.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountReply implements Serializable {
    private int code;
    private String msg;

    private static final long serialVersionUID = 1L;
}
