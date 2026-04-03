package com.exchange.common.redis.idemp.utils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IdempKey {
    final public String service;
    final public String scope;
    final public String idempId;
}
