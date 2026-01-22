package com.exchange.common.kafka.utils;

public class Topics {
    static public String form(String... args) {
        return String.join(".", args);
    }
}
