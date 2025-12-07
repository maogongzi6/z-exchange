package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ActionType {
    UNKNOWN(0),
    POSTING(1),;

    @EnumValue
    final public int code;

    ActionType(int code) {
        this.code = code;
    }
}
