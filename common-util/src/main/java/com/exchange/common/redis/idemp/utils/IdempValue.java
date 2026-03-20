package com.exchange.common.redis.idemp.utils;

import com.exchange.common.redis.idemp.constant.CommonIdempStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IdempValue {
    final public CommonIdempStatus status;
    final public String hash;
    final public String token;
    final public String content;
}
