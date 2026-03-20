package com.exchange.common.utils;

import org.apache.logging.log4j.util.Strings;

public class ValidateHelper {
    public static boolean isEmpty(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            str = str.trim();
            return Strings.isEmpty(str);
        }
        return value == null;
    }
}
