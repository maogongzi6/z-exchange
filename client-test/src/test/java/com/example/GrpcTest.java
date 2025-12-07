package com.example;


import com.example.pojo.ProtobufBeanHelper;
import com.example.pojo.ledger.account.CreateAccountRequest;
import com.example.pojo.ledger.post.LedgerEntry;
import com.example.pojo.ledger.post.PostTransactionRequest;
import com.example.proto.ledger.account.AccountServiceGrpc;
import com.example.proto.ledger.account.CreateAccountReplyPb;
import com.example.proto.ledger.account.CreateAccountRequestPb;
import com.example.proto.ledger.common.LedgerDirectionPb;
import com.example.proto.ledger.post.PostServiceGrpc;
import com.example.proto.ledger.post.PostTransactionReplyPb;
import com.example.proto.ledger.post.PostTransactionRequestPb;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.Test;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class GrpcTest {
//    @GrpcClient("user")
//   private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;
    @GrpcClient("post-client")
    private PostServiceGrpc.PostServiceBlockingStub postServiceBlockingStub;
    @GrpcClient("account-client")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Test
    public void testPostConnect() throws IOException {


        PostTransactionRequest req = PostTransactionRequest.builder().legerTransactionId("txn-id").entries(
                List.of(LedgerEntry.builder().accountId("a-1").direction(LedgerDirectionPb.LedgerDirection_Debit).build())
        ).build();
        PostTransactionRequestPb.Builder b = PostTransactionRequestPb.newBuilder();
        ProtobufBeanHelper.toProto(b, req);
        PostTransactionReplyPb reply = postServiceBlockingStub.postTransaction(b.build());
        System.out.println(reply.getMsg());

    }

    @Test
    public void testAccountConnect() throws IOException {
        CreateAccountRequest req = new CreateAccountRequest();
        CreateAccountRequestPb.Builder b = CreateAccountRequestPb.newBuilder();
        ProtobufBeanHelper.toProto(b, req);
        CreateAccountReplyPb reply = accountServiceBlockingStub.createAccount(b.build());
        System.out.println(reply.getMsg());
    }

//    @Test
//    public void testRawConnect() {
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9091).usePlaintext().build();
//        UserServiceGrpc.UserServiceBlockingStub bookStub = UserServiceGrpc.newBlockingStub(channel);
//        UserReply reply = bookStub.queryUser(UserRequest.newBuilder().setId(1234).build());
//        System.out.println(reply.getData());
//         channel.shutdown();
//    }
}
