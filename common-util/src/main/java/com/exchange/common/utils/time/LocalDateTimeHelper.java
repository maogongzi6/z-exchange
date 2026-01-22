package com.exchange.common.utils.time;

import java.time.LocalDateTime;

public class LocalDateTimeHelper {
    static public LocalDateTime nowAfterMs(long ms) {
        return LocalDateTime.now().plusNanos(ms * 1000000);
    }
}
