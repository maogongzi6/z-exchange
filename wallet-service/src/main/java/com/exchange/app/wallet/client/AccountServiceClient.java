package com.exchange.app.wallet.client;

import com.exchange.proto.ledger.account.AccountServiceGrpc;
import com.exchange.proto.ledger.account.CreateAccountReplyPb;
import com.exchange.proto.ledger.account.CreateAccountRequestPb;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class AccountServiceClient {
    @GrpcClient("account-client")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    public CreateAccountReplyPb createAccount(CreateAccountRequestPb req) {
        return accountServiceBlockingStub.createAccount(req);
    }
}
