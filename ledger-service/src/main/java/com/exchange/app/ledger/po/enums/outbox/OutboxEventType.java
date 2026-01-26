package com.exchange.app.ledger.po.enums.outbox;

public enum OutboxEventType {
    UNKNOWN(0),
    LEDGER_POST_REPLY(1),;

    // @EnumValue
    final public int code;

    OutboxEventType(int code) {
        this.code = code;
    }
}
