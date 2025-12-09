package com.exchange.common.result;

import com.exchange.proto.common.error.ErrorCodePb;

public interface PbMappableErrorCode {
    ErrorCodePb toProto();
    String getMessage();
}
