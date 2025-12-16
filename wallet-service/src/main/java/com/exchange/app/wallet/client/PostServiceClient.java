package com.exchange.app.wallet.client;

import com.exchange.app.wallet.result.ErrorCode;
import com.exchange.app.wallet.result.Result;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.post.PostServiceGrpc;
import com.exchange.proto.ledger.post.PostTransactionReplyPb;
import com.exchange.proto.ledger.post.PostTransactionRequestPb;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class PostServiceClient {
    @GrpcClient("ledger-client")
    private PostServiceGrpc.PostServiceBlockingStub postServiceBlockingStub;

    public Result<PostTransactionReplyPb> postTransaction(PostTransactionRequestPb req) {
        PostTransactionReplyPb reply = postServiceBlockingStub.postTransaction(req);
        if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            return Result.fail(ErrorCode.POST_TRANSACTION_FAILED, reply.getError().getMessage());
        }
        return Result.success(reply);
    }
}
