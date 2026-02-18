package com.exchange.common.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum GlobalServiceId {
    UNKNOWN(0, "unknown"),
    LEDGER(1, "ledger"),
    WALLET(2, "wallet"),;

    final public int id;
    final public String code;

    GlobalServiceId(int id, String code) {
        this.id = id;
        this.code = code;
    }
}
