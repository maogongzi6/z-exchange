package com.exchange.common;

import com.exchange.proto.common.error.ErrorCodePb;

public interface PbMappableErrorCode {
    ErrorCodePb toProto();
    String getMessage();
}
