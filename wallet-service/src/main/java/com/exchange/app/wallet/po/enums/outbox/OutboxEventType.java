package com.exchange.app.wallet.po.enums.outbox;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum OutboxEventType {
    UNKNOWN(0),
    LEDGER_POST(1),;

    // @EnumValue
    final public int code;

    OutboxEventType(int code) {
        this.code = code;
    }
}
