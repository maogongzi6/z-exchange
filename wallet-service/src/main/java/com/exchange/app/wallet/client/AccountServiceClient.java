package com.exchange.app.wallet.client;

import com.exchange.app.wallet.result.WalletRemoteErrorWrapper;
import com.exchange.app.wallet.result.WalletServiceErrorCode;
import com.exchange.common.result.Result;
import com.exchange.proto.common.error.ErrorCodePb;
import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccountServiceClient {
    @GrpcClient("account-client")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    public Result<CreateAccountReplyPb> createAccount(CreateAccountRequestPb req) {
        CreateAccountReplyPb reply = accountServiceBlockingStub.createAccount(req);
        if (reply.getError().getCode() != ErrorCodePb.ERROR_OK) {
            log.error("create_account failed: {}", reply.getError());
            return WalletRemoteErrorWrapper.failure(WalletServiceErrorCode.CREATE_ACCOUNT_FAILED, "ledger.account", reply.getError());
        }
        return Result.success(reply);
    }
}
