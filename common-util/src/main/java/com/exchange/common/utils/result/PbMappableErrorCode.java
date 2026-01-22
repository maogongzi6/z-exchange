package com.exchange.common.utils.result;

import com.exchange.proto.common.error.ErrorCodePb;

public interface PbMappableErrorCode {
    ErrorCodePb toProto();
    String getMessage();
}
