package backend.academy.linktracker.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ScrapperServiceGrpc {

    private ScrapperServiceGrpc() {}

    public static final java.lang.String SERVICE_NAME = "linktracker.grpc.ScrapperService";

    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.RegisterChatRequest, backend.academy.linktracker.grpc.Empty>
            getRegisterChatMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "RegisterChat",
            requestType = backend.academy.linktracker.grpc.RegisterChatRequest.class,
            responseType = backend.academy.linktracker.grpc.Empty.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.RegisterChatRequest, backend.academy.linktracker.grpc.Empty>
            getRegisterChatMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.RegisterChatRequest, backend.academy.linktracker.grpc.Empty>
                getRegisterChatMethod;
        if ((getRegisterChatMethod = ScrapperServiceGrpc.getRegisterChatMethod) == null) {
            synchronized (ScrapperServiceGrpc.class) {
                if ((getRegisterChatMethod = ScrapperServiceGrpc.getRegisterChatMethod) == null) {
                    ScrapperServiceGrpc.getRegisterChatMethod = getRegisterChatMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.RegisterChatRequest,
                                    backend.academy.linktracker.grpc.Empty>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterChat"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.RegisterChatRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.Empty.getDefaultInstance()))
                            .setSchemaDescriptor(new ScrapperServiceMethodDescriptorSupplier("RegisterChat"))
                            .build();
                }
            }
        }
        return getRegisterChatMethod;
    }

    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.DeleteChatRequest, backend.academy.linktracker.grpc.Empty>
            getDeleteChatMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "DeleteChat",
            requestType = backend.academy.linktracker.grpc.DeleteChatRequest.class,
            responseType = backend.academy.linktracker.grpc.Empty.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.DeleteChatRequest, backend.academy.linktracker.grpc.Empty>
            getDeleteChatMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.DeleteChatRequest, backend.academy.linktracker.grpc.Empty>
                getDeleteChatMethod;
        if ((getDeleteChatMethod = ScrapperServiceGrpc.getDeleteChatMethod) == null) {
            synchronized (ScrapperServiceGrpc.class) {
                if ((getDeleteChatMethod = ScrapperServiceGrpc.getDeleteChatMethod) == null) {
                    ScrapperServiceGrpc.getDeleteChatMethod = getDeleteChatMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.DeleteChatRequest,
                                    backend.academy.linktracker.grpc.Empty>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteChat"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.DeleteChatRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.Empty.getDefaultInstance()))
                            .setSchemaDescriptor(new ScrapperServiceMethodDescriptorSupplier("DeleteChat"))
                            .build();
                }
            }
        }
        return getDeleteChatMethod;
    }

    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.ListLinksRequest,
                    backend.academy.linktracker.grpc.ListLinksResponse>
            getListLinksMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "ListLinks",
            requestType = backend.academy.linktracker.grpc.ListLinksRequest.class,
            responseType = backend.academy.linktracker.grpc.ListLinksResponse.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.ListLinksRequest,
                    backend.academy.linktracker.grpc.ListLinksResponse>
            getListLinksMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.ListLinksRequest,
                        backend.academy.linktracker.grpc.ListLinksResponse>
                getListLinksMethod;
        if ((getListLinksMethod = ScrapperServiceGrpc.getListLinksMethod) == null) {
            synchronized (ScrapperServiceGrpc.class) {
                if ((getListLinksMethod = ScrapperServiceGrpc.getListLinksMethod) == null) {
                    ScrapperServiceGrpc.getListLinksMethod = getListLinksMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.ListLinksRequest,
                                    backend.academy.linktracker.grpc.ListLinksResponse>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListLinks"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.ListLinksRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.ListLinksResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new ScrapperServiceMethodDescriptorSupplier("ListLinks"))
                            .build();
                }
            }
        }
        return getListLinksMethod;
    }

    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.AddLinkRequest, backend.academy.linktracker.grpc.LinkResponse>
            getAddLinkMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "AddLink",
            requestType = backend.academy.linktracker.grpc.AddLinkRequest.class,
            responseType = backend.academy.linktracker.grpc.LinkResponse.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.AddLinkRequest, backend.academy.linktracker.grpc.LinkResponse>
            getAddLinkMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.AddLinkRequest, backend.academy.linktracker.grpc.LinkResponse>
                getAddLinkMethod;
        if ((getAddLinkMethod = ScrapperServiceGrpc.getAddLinkMethod) == null) {
            synchronized (ScrapperServiceGrpc.class) {
                if ((getAddLinkMethod = ScrapperServiceGrpc.getAddLinkMethod) == null) {
                    ScrapperServiceGrpc.getAddLinkMethod = getAddLinkMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.AddLinkRequest,
                                    backend.academy.linktracker.grpc.LinkResponse>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AddLink"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.AddLinkRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.LinkResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new ScrapperServiceMethodDescriptorSupplier("AddLink"))
                            .build();
                }
            }
        }
        return getAddLinkMethod;
    }

    private static volatile io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.RemoveLinkRequest, backend.academy.linktracker.grpc.LinkResponse>
            getRemoveLinkMethod;

    @io.grpc.stub.annotations.RpcMethod(
            fullMethodName = SERVICE_NAME + '/' + "RemoveLink",
            requestType = backend.academy.linktracker.grpc.RemoveLinkRequest.class,
            responseType = backend.academy.linktracker.grpc.LinkResponse.class,
            methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<
                    backend.academy.linktracker.grpc.RemoveLinkRequest, backend.academy.linktracker.grpc.LinkResponse>
            getRemoveLinkMethod() {
        io.grpc.MethodDescriptor<
                        backend.academy.linktracker.grpc.RemoveLinkRequest,
                        backend.academy.linktracker.grpc.LinkResponse>
                getRemoveLinkMethod;
        if ((getRemoveLinkMethod = ScrapperServiceGrpc.getRemoveLinkMethod) == null) {
            synchronized (ScrapperServiceGrpc.class) {
                if ((getRemoveLinkMethod = ScrapperServiceGrpc.getRemoveLinkMethod) == null) {
                    ScrapperServiceGrpc.getRemoveLinkMethod = getRemoveLinkMethod = io.grpc.MethodDescriptor
                            .<backend.academy.linktracker.grpc.RemoveLinkRequest,
                                    backend.academy.linktracker.grpc.LinkResponse>
                                    newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RemoveLink"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.RemoveLinkRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                    backend.academy.linktracker.grpc.LinkResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new ScrapperServiceMethodDescriptorSupplier("RemoveLink"))
                            .build();
                }
            }
        }
        return getRemoveLinkMethod;
    }

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static ScrapperServiceStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceStub>() {
                    @java.lang.Override
                    public ScrapperServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new ScrapperServiceStub(channel, callOptions);
                    }
                };
        return ScrapperServiceStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static ScrapperServiceBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceBlockingV2Stub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceBlockingV2Stub>() {
                    @java.lang.Override
                    public ScrapperServiceBlockingV2Stub newStub(
                            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new ScrapperServiceBlockingV2Stub(channel, callOptions);
                    }
                };
        return ScrapperServiceBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static ScrapperServiceBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceBlockingStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceBlockingStub>() {
                    @java.lang.Override
                    public ScrapperServiceBlockingStub newStub(
                            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new ScrapperServiceBlockingStub(channel, callOptions);
                    }
                };
        return ScrapperServiceBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static ScrapperServiceFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceFutureStub> factory =
                new io.grpc.stub.AbstractStub.StubFactory<ScrapperServiceFutureStub>() {
                    @java.lang.Override
                    public ScrapperServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                        return new ScrapperServiceFutureStub(channel, callOptions);
                    }
                };
        return ScrapperServiceFutureStub.newStub(factory, channel);
    }

    /**
     */
    public interface AsyncService {

        /**
         */
        default void registerChat(
                backend.academy.linktracker.grpc.RegisterChatRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterChatMethod(), responseObserver);
        }

        /**
         */
        default void deleteChat(
                backend.academy.linktracker.grpc.DeleteChatRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteChatMethod(), responseObserver);
        }

        /**
         */
        default void listLinks(
                backend.academy.linktracker.grpc.ListLinksRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.ListLinksResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListLinksMethod(), responseObserver);
        }

        /**
         */
        default void addLink(
                backend.academy.linktracker.grpc.AddLinkRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAddLinkMethod(), responseObserver);
        }

        /**
         */
        default void removeLink(
                backend.academy.linktracker.grpc.RemoveLinkRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRemoveLinkMethod(), responseObserver);
        }
    }

    /**
     * Base class for the server implementation of the service ScrapperService.
     */
    public abstract static class ScrapperServiceImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return ScrapperServiceGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service ScrapperService.
     */
    public static final class ScrapperServiceStub extends io.grpc.stub.AbstractAsyncStub<ScrapperServiceStub> {
        private ScrapperServiceStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ScrapperServiceStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ScrapperServiceStub(channel, callOptions);
        }

        /**
         */
        public void registerChat(
                backend.academy.linktracker.grpc.RegisterChatRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getRegisterChatMethod(), getCallOptions()), request, responseObserver);
        }

        /**
         */
        public void deleteChat(
                backend.academy.linktracker.grpc.DeleteChatRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getDeleteChatMethod(), getCallOptions()), request, responseObserver);
        }

        /**
         */
        public void listLinks(
                backend.academy.linktracker.grpc.ListLinksRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.ListLinksResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getListLinksMethod(), getCallOptions()), request, responseObserver);
        }

        /**
         */
        public void addLink(
                backend.academy.linktracker.grpc.AddLinkRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getAddLinkMethod(), getCallOptions()), request, responseObserver);
        }

        /**
         */
        public void removeLink(
                backend.academy.linktracker.grpc.RemoveLinkRequest request,
                io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getRemoveLinkMethod(), getCallOptions()), request, responseObserver);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service ScrapperService.
     */
    public static final class ScrapperServiceBlockingV2Stub
            extends io.grpc.stub.AbstractBlockingStub<ScrapperServiceBlockingV2Stub> {
        private ScrapperServiceBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ScrapperServiceBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ScrapperServiceBlockingV2Stub(channel, callOptions);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty registerChat(
                backend.academy.linktracker.grpc.RegisterChatRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getRegisterChatMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty deleteChat(
                backend.academy.linktracker.grpc.DeleteChatRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getDeleteChatMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.ListLinksResponse listLinks(
                backend.academy.linktracker.grpc.ListLinksRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getListLinksMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.LinkResponse addLink(
                backend.academy.linktracker.grpc.AddLinkRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getAddLinkMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.LinkResponse removeLink(
                backend.academy.linktracker.grpc.RemoveLinkRequest request) throws io.grpc.StatusException {
            return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
                    getChannel(), getRemoveLinkMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service ScrapperService.
     */
    public static final class ScrapperServiceBlockingStub
            extends io.grpc.stub.AbstractBlockingStub<ScrapperServiceBlockingStub> {
        private ScrapperServiceBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ScrapperServiceBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ScrapperServiceBlockingStub(channel, callOptions);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty registerChat(
                backend.academy.linktracker.grpc.RegisterChatRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getRegisterChatMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.Empty deleteChat(
                backend.academy.linktracker.grpc.DeleteChatRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getDeleteChatMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.ListLinksResponse listLinks(
                backend.academy.linktracker.grpc.ListLinksRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getListLinksMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.LinkResponse addLink(
                backend.academy.linktracker.grpc.AddLinkRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getAddLinkMethod(), getCallOptions(), request);
        }

        /**
         */
        public backend.academy.linktracker.grpc.LinkResponse removeLink(
                backend.academy.linktracker.grpc.RemoveLinkRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getRemoveLinkMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service ScrapperService.
     */
    public static final class ScrapperServiceFutureStub
            extends io.grpc.stub.AbstractFutureStub<ScrapperServiceFutureStub> {
        private ScrapperServiceFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ScrapperServiceFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ScrapperServiceFutureStub(channel, callOptions);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.Empty> registerChat(
                backend.academy.linktracker.grpc.RegisterChatRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getRegisterChatMethod(), getCallOptions()), request);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.Empty> deleteChat(
                backend.academy.linktracker.grpc.DeleteChatRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getDeleteChatMethod(), getCallOptions()), request);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.ListLinksResponse>
                listLinks(backend.academy.linktracker.grpc.ListLinksRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getListLinksMethod(), getCallOptions()), request);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.LinkResponse>
                addLink(backend.academy.linktracker.grpc.AddLinkRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getAddLinkMethod(), getCallOptions()), request);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<backend.academy.linktracker.grpc.LinkResponse>
                removeLink(backend.academy.linktracker.grpc.RemoveLinkRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getRemoveLinkMethod(), getCallOptions()), request);
        }
    }

    private static final int METHODID_REGISTER_CHAT = 0;
    private static final int METHODID_DELETE_CHAT = 1;
    private static final int METHODID_LIST_LINKS = 2;
    private static final int METHODID_ADD_LINK = 3;
    private static final int METHODID_REMOVE_LINK = 4;

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
                case METHODID_REGISTER_CHAT:
                    serviceImpl.registerChat(
                            (backend.academy.linktracker.grpc.RegisterChatRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty>) responseObserver);
                    break;
                case METHODID_DELETE_CHAT:
                    serviceImpl.deleteChat(
                            (backend.academy.linktracker.grpc.DeleteChatRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.Empty>) responseObserver);
                    break;
                case METHODID_LIST_LINKS:
                    serviceImpl.listLinks(
                            (backend.academy.linktracker.grpc.ListLinksRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.ListLinksResponse>)
                                    responseObserver);
                    break;
                case METHODID_ADD_LINK:
                    serviceImpl.addLink(
                            (backend.academy.linktracker.grpc.AddLinkRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse>)
                                    responseObserver);
                    break;
                case METHODID_REMOVE_LINK:
                    serviceImpl.removeLink(
                            (backend.academy.linktracker.grpc.RemoveLinkRequest) request,
                            (io.grpc.stub.StreamObserver<backend.academy.linktracker.grpc.LinkResponse>)
                                    responseObserver);
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
                        getRegisterChatMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.RegisterChatRequest,
                                backend.academy.linktracker.grpc.Empty>(service, METHODID_REGISTER_CHAT)))
                .addMethod(
                        getDeleteChatMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.DeleteChatRequest,
                                backend.academy.linktracker.grpc.Empty>(service, METHODID_DELETE_CHAT)))
                .addMethod(
                        getListLinksMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.ListLinksRequest,
                                backend.academy.linktracker.grpc.ListLinksResponse>(service, METHODID_LIST_LINKS)))
                .addMethod(
                        getAddLinkMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.AddLinkRequest,
                                backend.academy.linktracker.grpc.LinkResponse>(service, METHODID_ADD_LINK)))
                .addMethod(
                        getRemoveLinkMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(new MethodHandlers<
                                backend.academy.linktracker.grpc.RemoveLinkRequest,
                                backend.academy.linktracker.grpc.LinkResponse>(service, METHODID_REMOVE_LINK)))
                .build();
    }

    private abstract static class ScrapperServiceBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
        ScrapperServiceBaseDescriptorSupplier() {}

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return backend.academy.linktracker.grpc.LinkTrackerProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("ScrapperService");
        }
    }

    private static final class ScrapperServiceFileDescriptorSupplier extends ScrapperServiceBaseDescriptorSupplier {
        ScrapperServiceFileDescriptorSupplier() {}
    }

    private static final class ScrapperServiceMethodDescriptorSupplier extends ScrapperServiceBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
        private final java.lang.String methodName;

        ScrapperServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (ScrapperServiceGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                            .setSchemaDescriptor(new ScrapperServiceFileDescriptorSupplier())
                            .addMethod(getRegisterChatMethod())
                            .addMethod(getDeleteChatMethod())
                            .addMethod(getListLinksMethod())
                            .addMethod(getAddLinkMethod())
                            .addMethod(getRemoveLinkMethod())
                            .build();
                }
            }
        }
        return result;
    }
}
