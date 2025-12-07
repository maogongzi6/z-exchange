package com.example.service;

import com.example.exception.ErrorCode;
import com.example.pojo.ProtobufBeanHelper;
import com.example.pojo.wallet.CreateWalletReply;
import com.example.pojo.wallet.CreateWalletRequest;
import com.example.processor.CreateWalletProcessor;
import com.example.proto.wallet.CreateWalletReplyPb;
import com.example.proto.wallet.CreateWalletRequestPb;
import com.example.proto.wallet.WalletServiceGrpc;
import com.example.utils.ErrorHelper;
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
            CreateWalletRequest req = ProtobufBeanHelper.toPojoBean(CreateWalletRequest.class, request);
            CreateWalletReply resp = createWalletProcessor.createWallet(req);
            b = CreateWalletReplyPb.newBuilder();
            ProtobufBeanHelper.toProtoBean(b, resp);
        } catch (Exception e) {
            ErrorCode replyCode = ErrorHelper.getErrorCode(e);
            b = CreateWalletReplyPb.newBuilder().setCode(replyCode.code).setMsg(replyCode.message);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
