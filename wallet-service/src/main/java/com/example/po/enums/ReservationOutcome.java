package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ReservationOutcome {
    UNKNOWN(0),
    NOT_DONE(1),
    CONSUMED(2),
    RELEASED(3),
    PARTIAL(4),
    CANCELLED(5),;

    @EnumValue
    final public int code;

    ReservationOutcome(int code) {
        this.code = code;
    }
}
