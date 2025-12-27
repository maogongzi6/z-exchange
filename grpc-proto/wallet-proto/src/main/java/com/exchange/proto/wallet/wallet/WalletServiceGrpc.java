package com.exchange.proto.wallet.wallet;

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
    comments = "Source: wallet/wallet_service.proto")
public final class WalletServiceGrpc {

  private WalletServiceGrpc() {}

  public static final String SERVICE_NAME = "wallet.WalletService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.CreateWalletRequestPb,
      com.exchange.proto.wallet.wallet.CreateWalletReplyPb> getCreateWalletMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "createWallet",
      requestType = com.exchange.proto.wallet.wallet.CreateWalletRequestPb.class,
      responseType = com.exchange.proto.wallet.wallet.CreateWalletReplyPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.CreateWalletRequestPb,
      com.exchange.proto.wallet.wallet.CreateWalletReplyPb> getCreateWalletMethod() {
    io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.CreateWalletRequestPb, com.exchange.proto.wallet.wallet.CreateWalletReplyPb> getCreateWalletMethod;
    if ((getCreateWalletMethod = WalletServiceGrpc.getCreateWalletMethod) == null) {
      synchronized (WalletServiceGrpc.class) {
        if ((getCreateWalletMethod = WalletServiceGrpc.getCreateWalletMethod) == null) {
          WalletServiceGrpc.getCreateWalletMethod = getCreateWalletMethod =
              io.grpc.MethodDescriptor.<com.exchange.proto.wallet.wallet.CreateWalletRequestPb, com.exchange.proto.wallet.wallet.CreateWalletReplyPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "createWallet"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.CreateWalletRequestPb.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.CreateWalletReplyPb.getDefaultInstance()))
              .setSchemaDescriptor(new WalletServiceMethodDescriptorSupplier("createWallet"))
              .build();
        }
      }
    }
    return getCreateWalletMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb,
      com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> getAtomicTransactionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "atomicTransaction",
      requestType = com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb.class,
      responseType = com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb,
      com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> getAtomicTransactionMethod() {
    io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb, com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> getAtomicTransactionMethod;
    if ((getAtomicTransactionMethod = WalletServiceGrpc.getAtomicTransactionMethod) == null) {
      synchronized (WalletServiceGrpc.class) {
        if ((getAtomicTransactionMethod = WalletServiceGrpc.getAtomicTransactionMethod) == null) {
          WalletServiceGrpc.getAtomicTransactionMethod = getAtomicTransactionMethod =
              io.grpc.MethodDescriptor.<com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb, com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "atomicTransaction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb.getDefaultInstance()))
              .setSchemaDescriptor(new WalletServiceMethodDescriptorSupplier("atomicTransaction"))
              .build();
        }
      }
    }
    return getAtomicTransactionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb,
      com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> getReserveTransactionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "reserveTransaction",
      requestType = com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb.class,
      responseType = com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb,
      com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> getReserveTransactionMethod() {
    io.grpc.MethodDescriptor<com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb, com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> getReserveTransactionMethod;
    if ((getReserveTransactionMethod = WalletServiceGrpc.getReserveTransactionMethod) == null) {
      synchronized (WalletServiceGrpc.class) {
        if ((getReserveTransactionMethod = WalletServiceGrpc.getReserveTransactionMethod) == null) {
          WalletServiceGrpc.getReserveTransactionMethod = getReserveTransactionMethod =
              io.grpc.MethodDescriptor.<com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb, com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "reserveTransaction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb.getDefaultInstance()))
              .setSchemaDescriptor(new WalletServiceMethodDescriptorSupplier("reserveTransaction"))
              .build();
        }
      }
    }
    return getReserveTransactionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WalletServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WalletServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WalletServiceStub>() {
        @java.lang.Override
        public WalletServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WalletServiceStub(channel, callOptions);
        }
      };
    return WalletServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WalletServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WalletServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WalletServiceBlockingStub>() {
        @java.lang.Override
        public WalletServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WalletServiceBlockingStub(channel, callOptions);
        }
      };
    return WalletServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WalletServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WalletServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WalletServiceFutureStub>() {
        @java.lang.Override
        public WalletServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WalletServiceFutureStub(channel, callOptions);
        }
      };
    return WalletServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class WalletServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void createWallet(com.exchange.proto.wallet.wallet.CreateWalletRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.CreateWalletReplyPb> responseObserver) {
      asyncUnimplementedUnaryCall(getCreateWalletMethod(), responseObserver);
    }

    /**
     */
    public void atomicTransaction(com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> responseObserver) {
      asyncUnimplementedUnaryCall(getAtomicTransactionMethod(), responseObserver);
    }

    /**
     */
    public void reserveTransaction(com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> responseObserver) {
      asyncUnimplementedUnaryCall(getReserveTransactionMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCreateWalletMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.exchange.proto.wallet.wallet.CreateWalletRequestPb,
                com.exchange.proto.wallet.wallet.CreateWalletReplyPb>(
                  this, METHODID_CREATE_WALLET)))
          .addMethod(
            getAtomicTransactionMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb,
                com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb>(
                  this, METHODID_ATOMIC_TRANSACTION)))
          .addMethod(
            getReserveTransactionMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb,
                com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb>(
                  this, METHODID_RESERVE_TRANSACTION)))
          .build();
    }
  }

  /**
   */
  public static final class WalletServiceStub extends io.grpc.stub.AbstractAsyncStub<WalletServiceStub> {
    private WalletServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WalletServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WalletServiceStub(channel, callOptions);
    }

    /**
     */
    public void createWallet(com.exchange.proto.wallet.wallet.CreateWalletRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.CreateWalletReplyPb> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getCreateWalletMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void atomicTransaction(com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAtomicTransactionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void reserveTransaction(com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb request,
        io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getReserveTransactionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class WalletServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<WalletServiceBlockingStub> {
    private WalletServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WalletServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WalletServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.exchange.proto.wallet.wallet.CreateWalletReplyPb createWallet(com.exchange.proto.wallet.wallet.CreateWalletRequestPb request) {
      return blockingUnaryCall(
          getChannel(), getCreateWalletMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb atomicTransaction(com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb request) {
      return blockingUnaryCall(
          getChannel(), getAtomicTransactionMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb reserveTransaction(com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb request) {
      return blockingUnaryCall(
          getChannel(), getReserveTransactionMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class WalletServiceFutureStub extends io.grpc.stub.AbstractFutureStub<WalletServiceFutureStub> {
    private WalletServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WalletServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WalletServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.proto.wallet.wallet.CreateWalletReplyPb> createWallet(
        com.exchange.proto.wallet.wallet.CreateWalletRequestPb request) {
      return futureUnaryCall(
          getChannel().newCall(getCreateWalletMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb> atomicTransaction(
        com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb request) {
      return futureUnaryCall(
          getChannel().newCall(getAtomicTransactionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb> reserveTransaction(
        com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb request) {
      return futureUnaryCall(
          getChannel().newCall(getReserveTransactionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_WALLET = 0;
  private static final int METHODID_ATOMIC_TRANSACTION = 1;
  private static final int METHODID_RESERVE_TRANSACTION = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final WalletServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(WalletServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_WALLET:
          serviceImpl.createWallet((com.exchange.proto.wallet.wallet.CreateWalletRequestPb) request,
              (io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.CreateWalletReplyPb>) responseObserver);
          break;
        case METHODID_ATOMIC_TRANSACTION:
          serviceImpl.atomicTransaction((com.exchange.proto.wallet.wallet.AtomicTransactionRequestPb) request,
              (io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.AtomicTransactionReplyPb>) responseObserver);
          break;
        case METHODID_RESERVE_TRANSACTION:
          serviceImpl.reserveTransaction((com.exchange.proto.wallet.wallet.ReserveTransactionRequestPb) request,
              (io.grpc.stub.StreamObserver<com.exchange.proto.wallet.wallet.ReserveTransactionReplyPb>) responseObserver);
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

  private static abstract class WalletServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WalletServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.exchange.proto.wallet.wallet.WalletServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WalletService");
    }
  }

  private static final class WalletServiceFileDescriptorSupplier
      extends WalletServiceBaseDescriptorSupplier {
    WalletServiceFileDescriptorSupplier() {}
  }

  private static final class WalletServiceMethodDescriptorSupplier
      extends WalletServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    WalletServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (WalletServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WalletServiceFileDescriptorSupplier())
              .addMethod(getCreateWalletMethod())
              .addMethod(getAtomicTransactionMethod())
              .addMethod(getReserveTransactionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
