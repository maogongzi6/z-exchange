package com.exchange.common.utils;

public class StringHelper {
    static public String removePrefix(String str, String prefix) {
        if (str.startsWith(prefix)) {
            StringBuilder sb = new StringBuilder(str);
            sb.delete(0, prefix.length());
            return sb.toString();
        }
        return str;
    }
}
