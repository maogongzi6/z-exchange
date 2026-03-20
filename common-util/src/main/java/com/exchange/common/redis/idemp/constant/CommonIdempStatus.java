package com.exchange.common.redis.idemp.constant;

public enum CommonIdempStatus {
    UNKNOWN(0, "UNKNOWN"),
    PENDING(1, "P"),
    ACCEPTED(2, "A"),;

    final public int id;
    final public String code;

    CommonIdempStatus(int id, String code) {
        this.id = id;
        this.code = code;
    }

    static public boolean isUnknown(CommonIdempStatus status) {
        return status == null || status == UNKNOWN;
    }

    static public CommonIdempStatus getByCode(String code) {
        for (CommonIdempStatus c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
