package com.example.client;

import com.example.pojo.ProtobufBeanHelper;
import com.example.pojo.ledger.account.CreateAccountReply;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.proto.ledger.account.AccountServiceGrpc;
import com.example.proto.ledger.account.CreateAccountReplyPb;
import com.example.proto.ledger.account.CreateAccountRequestPb;
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
