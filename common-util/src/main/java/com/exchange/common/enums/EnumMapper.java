package com.exchange.common.enums;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.HashMap;
import java.util.Map;

public class EnumMapper<E, T> {
    private final BiMap<E, T> map = HashBiMap.create();

    public EnumMapper(Map<E, T> map) {
        if (map == null) {
            map = new HashMap<>();
        }
        this.map.putAll(map);
    }

    public T to(E value) {
        return map.get(value);
    }

    public E from(T value) {
        return map.inverse().get(value);
    }
}
