package com.exchange.app.wallet.service;

import com.exchange.app.wallet.exception.ErrorCode;
import com.exchange.app.wallet.processor.CreateWalletProcessor;
import com.exchange.app.wallet.utils.ErrorHelper;
import com.exchange.proto.wallet.wallet.CreateWalletReplyPb;
import com.exchange.proto.wallet.wallet.CreateWalletRequestPb;
import com.exchange.proto.wallet.wallet.WalletServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {
    final private CreateWalletProcessor createWalletProcessor;

    @Override
    public void createWallet(CreateWalletRequestPb request, StreamObserver<CreateWalletReplyPb> responseObserver) {
        CreateWalletReplyPb.Builder b;
        try {
            CreateWalletReplyPb resp = createWalletProcessor.createWallet(request);
            b = resp.toBuilder();
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = CreateWalletReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }


}
