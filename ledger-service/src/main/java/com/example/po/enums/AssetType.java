package com.example.po.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum AssetType {
    UNKNOWN(0),
    NORMAL(1);

    @EnumValue
    final public int code;

    AssetType(int code) {
        this.code = code;
    }
}
