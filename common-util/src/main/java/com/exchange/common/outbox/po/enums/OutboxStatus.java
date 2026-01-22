package com.exchange.common.outbox.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum OutboxStatus {
    UNKNOWN(0),
    PENDING(1),
    SENT(2),
    DEAD(3),;

    @EnumValue
    final public int code;

    OutboxStatus(int code) {
        this.code = code;
    }
}
