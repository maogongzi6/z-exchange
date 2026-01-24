package com.exchange.common.kafka.utils;

public class Topics {
    static public String dlq(String topic) {
        return topic + ".dlq";
    }
}
