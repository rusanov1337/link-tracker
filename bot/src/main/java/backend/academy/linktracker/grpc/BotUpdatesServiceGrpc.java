package backend.academy.linktracker.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class BotUpdatesServiceGrpc {

    private BotUpdatesServiceGrpc() {}

    public static final java.lang.String SERVICE_NAME = "linktracker.grpc.BotUpdatesService";

    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.LinkUpdateRequest, backend.academy.linktracker.grpc.Empty>
            getProcessUpdateMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "ProcessUpdate",
            requestType = backend.academy.linktracker.grpc.LinkUpdateRequest.class,
            responseType = backend.academy.linktracker.grpc.Empty.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.LinkUpdateRequest, backend.academy.linktracker.grpc.Empty>
            getProcessUpdateMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.LinkUpdateRequest, backend.academy.linktracker.grpc.Empty>
                getProcessUpdateMethod;
        if ((getProcessUpdateMethod = BotUpdatesServiceGrpc.getProcessUpdateMethod) == null) {
            synchronized (BotUpdatesServiceGrpc.class) {
                if ((getProcessUpdateMethod = BotUpdatesServiceGrpc.getProcessUpdateMethod) == null) {
                    BotUpdatesServiceGrpc.getProcessUpdateMethod = getProcessUpdateMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.LinkUpdateRequest,
                                    backend.academy.linktracker.grpc.Empty>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ProcessUpdate"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.LinkUpdateRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.Empty.getDefaultInstance()))
                            .setSchemaDescriptor(new BotUpdatesServiceMethodDescriptorSupplier("ProcessUpdate"))
                            .build();
                }
            }
        }
        return getProcessUpdateMethod;
    }

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static BotUpdatesServiceStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceStub>() {
                    @java.lang.Override
                    public BotUpdatesServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new BotUpdatesServiceStub(channel, callOptions);
                    }
                };
        return BotUpdatesServiceStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static BotUpdatesServiceBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceBlockingV2Stub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceBlockingV2Stub>() {
                    @java.lang.Override
                    public BotUpdatesServiceBlockingV2Stub newStub(
                            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new BotUpdatesServiceBlockingV2Stub(channel, callOptions);
                    }
                };
        return BotUpdatesServiceBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static BotUpdatesServiceBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceBlockingStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceBlockingStub>() {
                    @java.lang.Override
                    public BotUpdatesServiceBlockingStub newStub(
                            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new BotUpdatesServiceBlockingStub(channel, callOptions);
                    }
                };
        return BotUpdatesServiceBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static BotUpdatesServiceFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceFutureStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<BotUpdatesServiceFutureStub>() {
                    @java.lang.Override
                    public BotUpdatesServiceFutureStub newStub(
                            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new BotUpdatesServiceFutureStub(channel, callOptions);
                    }
                };
        return BotUpdatesServiceFutureStub.newStub(factory, channel);
    }

    /**
     */
    public interface AsyncService {

        /**
         */
        default void processUpdate(
                backend.academy.linktracker.grpc.LinkUpdateRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getProcessUpdateMethod(), responseObserver);
        }
    }

    /**
     * Base class for the server implementation of the service BotUpdatesService.
     */
    public abstract static class BotUpdatesServiceImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return BotUpdatesServiceGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service BotUpdatesService.
     */
    public static final class BotUpdatesServiceStub extends io.grpc.stub.AbstractAsyncStub<BotUpdatesServiceStub> {
        private BotUpdatesServiceStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected BotUpdatesServiceStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BotUpdatesServiceStub(channel, callOptions);
        }

        /**
         */
        public void processUpdate(
                backend.academy.linktracker.grpc.LinkUpdateRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getProcessUpdateMethod(), getCallOptions()), request, responseObserver);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service BotUpdatesService.
     */
    public static final class BotUpdatesServiceBlockingV2Stub
            extends io.grpc.stub.AbstractBlockingStub<BotUpdatesServiceBlockingV2Stub> {
        private BotUpdatesServiceBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected BotUpdatesServiceBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BotUpdatesServiceBlockingV2Stub(channel, callOptions);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty processUpdate(
                backend.academy.linktracker.grpc.LinkUpdateRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getProcessUpdateMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service BotUpdatesService.
     */
    public static final class BotUpdatesServiceBlockingStub
            extends io.grpc.stub.AbstractBlockingStub<BotUpdatesServiceBlockingStub> {
        private BotUpdatesServiceBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected BotUpdatesServiceBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BotUpdatesServiceBlockingStub(channel, callOptions);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty processUpdate(
                backend.academy.linktracker.grpc.LinkUpdateRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getProcessUpdateMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service BotUpdatesService.
     */
    public static final class BotUpdatesServiceFutureStub
            extends io.grpc.stub.AbstractFutureStub<BotUpdatesServiceFutureStub> {
        private BotUpdatesServiceFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected BotUpdatesServiceFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BotUpdatesServiceFutureStub(channel, callOptions);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.Empty> processUpdate(
                backend.academy.linktracker.grpc.LinkUpdateRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getProcessUpdateMethod(), getCallOptions()), request);
        }
    }

    private static final int METHODID_PROCESS_UPDATE = 0;

    private static final class MethodHandlers<Req, Resp>
            implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
                    io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
                    io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
                    io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
        private final AsyncService serviceImpl;
        private final int methodId;

        MethodHandlers(AsyncService serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_PROCESS_UPDATE:
                    serviceImpl.processUpdate(
                            (backend.academy.linktracker.grpc.LinkUpdateRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty>) responseObserver);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                default:
                    throw new AssertionError();
            }
        }
    }

    public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
        return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
                .addMethod(
                        getProcessUpdateMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.LinkUpdateRequest,
                                backend.academy.linktracker.grpc.Empty>(service, METHODID_PROCESS_UPDATE)))
                .build();
    }

    private abstract static class BotUpdatesServiceBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
        BotUpdatesServiceBaseDescriptorSupplier() {}

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return backend.academy.linktracker.grpc.LinkTrackerProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("BotUpdatesService");
        }
    }

    private static final class BotUpdatesServiceFileDescriptorSupplier extends BotUpdatesServiceBaseDescriptorSupplier {
        BotUpdatesServiceFileDescriptorSupplier() {}
    }

    private static final class BotUpdatesServiceMethodDescriptorSupplier extends BotUpdatesServiceBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
        private final java.lang.String methodName;

        BotUpdatesServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (BotUpdatesServiceGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                            .setSchemaDescriptor(new BotUpdatesServiceFileDescriptorSupplier())
                            .addMethod(getProcessUpdateMethod())
                            .build();
                }
            }
        }
        return result;
    }
}
