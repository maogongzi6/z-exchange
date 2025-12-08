package com.exchange.proto.ledger.post;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.34.1)",
    comments = "Source: com/exchange/proto/ledger/post_service.proto")
public final class PostServiceGrpc {

  private PostServiceGrpc() {}

  public static final String SERVICE_NAME = "ledger.PostService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.exchange.proto.ledger.post.PostTransactionRequestPb,
      com.exchange.proto.ledger.post.PostTransactionReplyPb> getPostTransactionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "postTransaction",
      requestType = com.exchange.proto.ledger.post.PostTransactionRequestPb.class,
      responseType = com.exchange.proto.ledger.post.PostTransactionReplyPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.proto.ledger.post.PostTransactionRequestPb,
      com.exchange.proto.ledger.post.PostTransactionReplyPb> getPostTransactionMethod() {
    io.grpc.MethodDescriptor<com.exchange.proto.ledger.post.PostTransactionRequestPb, com.exchange.proto.ledger.post.PostTransactionReplyPb> getPostTransactionMethod;
    if ((getPostTransactionMethod = PostServiceGrpc.getPostTransactionMethod) == null) {
      synchronized (PostServiceGrpc.class) {
        if ((getPostTransactionMethod = PostServiceGrpc.getPostTransactionMethod) == null) {
          PostServiceGrpc.getPostTransactionMethod = getPostTransactionMethod =
              io.grpc.MethodDescriptor.<com.exchange.proto.ledger.post.PostTransactionRequestPb, com.exchange.proto.ledger.post.PostTransactionReplyPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "postTransaction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.ledger.post.PostTransactionRequestPb.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.ledger.post.PostTransactionReplyPb.getDefaultInstance()))
              .setSchemaDescriptor(new PostServiceMethodDescriptorSupplier("postTransaction"))
              .build();
        }
      }
    }
    return getPostTransactionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PostServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PostServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PostServiceStub>() {
        @java.lang.Override
        public PostServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PostServiceStub(channel, callOptions);
        }
      };
    return PostServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PostServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PostServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PostServiceBlockingStub>() {
        @java.lang.Override
        public PostServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PostServiceBlockingStub(channel, callOptions);
        }
      };
    return PostServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PostServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PostServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PostServiceFutureStub>() {
        @java.lang.Override
        public PostServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PostServiceFutureStub(channel, callOptions);
        }
      };
    return PostServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class PostServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void postTransaction(com.exchange.proto.ledger.post.PostTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.ledger.post.PostTransactionReplyPb> responseObserver) {
      asyncUnimplementedUnaryCall(getPostTransactionMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getPostTransactionMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.exchange.proto.ledger.post.PostTransactionRequestPb,
                com.exchange.proto.ledger.post.PostTransactionReplyPb>(
                  this, METHODID_POST_TRANSACTION)))
          .build();
    }
  }

  /**
   */
  public static final class PostServiceStub extends io.grpc.stub.AbstractAsyncStub<PostServiceStub> {
    private PostServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PostServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PostServiceStub(channel, callOptions);
    }

    /**
     */
    public void postTransaction(com.exchange.proto.ledger.post.PostTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.ledger.post.PostTransactionReplyPb> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getPostTransactionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class PostServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<PostServiceBlockingStub> {
    private PostServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PostServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PostServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.exchange.proto.ledger.post.PostTransactionReplyPb postTransaction(com.exchange.proto.ledger.post.PostTransactionRequestPb request) {
      return blockingUnaryCall(
          getChannel(), getPostTransactionMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class PostServiceFutureStub extends io.grpc.stub.AbstractFutureStub<PostServiceFutureStub> {
    private PostServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PostServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PostServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.proto.ledger.post.PostTransactionReplyPb> postTransaction(
        com.exchange.proto.ledger.post.PostTransactionRequestPb request) {
      return futureUnaryCall(
          getChannel().newCall(getPostTransactionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_POST_TRANSACTION = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PostServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(PostServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_POST_TRANSACTION:
          serviceImpl.postTransaction((com.exchange.proto.ledger.post.PostTransactionRequestPb) request,
              (io.grpc.stub.StreamObserver<com.exchange.proto.ledger.post.PostTransactionReplyPb>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class PostServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PostServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.exchange.proto.ledger.post.PostServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PostService");
    }
  }

  private static final class PostServiceFileDescriptorSupplier
      extends PostServiceBaseDescriptorSupplier {
    PostServiceFileDescriptorSupplier() {}
  }

  private static final class PostServiceMethodDescriptorSupplier
      extends PostServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    PostServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (PostServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PostServiceFileDescriptorSupplier())
              .addMethod(getPostTransactionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
