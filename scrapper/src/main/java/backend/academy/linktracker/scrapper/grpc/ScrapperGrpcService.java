package backend.academy.linktracker.scrapper.grpc;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.DeleteChatRequest;
import backend.academy.linktracker.grpc.Empty;
import backend.academy.linktracker.grpc.LinkResponse;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RegisterChatRequest;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import backend.academy.linktracker.scrapper.service.ScrapperLinkService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class ScrapperGrpcService extends ScrapperServiceGrpc.ScrapperServiceImplBase {

    private final ScrapperLinkService scrapperLinkService;

    public ScrapperGrpcService(ScrapperLinkService scrapperLinkService) {
        this.scrapperLinkService = scrapperLinkService;
    }

    @Override
    public void registerChat(RegisterChatRequest request, StreamObserver<Empty> responseObserver) {
        handle(responseObserver, () -> {
            validateChatId(request.getChatId());
            scrapperLinkService.registerChat(request.getChatId());
            return Empty.newBuilder().build();
        });
    }

    @Override
    public void deleteChat(DeleteChatRequest request, StreamObserver<Empty> responseObserver) {
        handle(responseObserver, () -> {
            validateChatId(request.getChatId());
            scrapperLinkService.deleteChat(request.getChatId());
            return Empty.newBuilder().build();
        });
    }

    @Override
    public void listLinks(ListLinksRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        handle(responseObserver, () -> {
            validateChatId(request.getChatId());
            var response = scrapperLinkService.getLinks(request.getChatId());
            return ListLinksResponse.newBuilder()
                    .addAllLinks(response.links().stream()
                            .map(this::toGrpcLinkResponse)
                            .toList())
                    .setSize(response.size())
                    .build();
        });
    }

    @Override
    public void addLink(AddLinkRequest request, StreamObserver<LinkResponse> responseObserver) {
        handle(responseObserver, () -> {
            validateChatId(request.getChatId());
            if (request.getLink().isBlank()) {
                throw new IllegalArgumentException("link must not be blank");
            }
            var response = scrapperLinkService.addLink(
                    request.getChatId(),
                    new backend.academy.linktracker.scrapper.api.dto.AddLinkRequest(
                            request.getLink(), request.getTagsList(), request.getFiltersList()));
            return toGrpcLinkResponse(response);
        });
    }

    @Override
    public void removeLink(RemoveLinkRequest request, StreamObserver<LinkResponse> responseObserver) {
        handle(responseObserver, () -> {
            validateChatId(request.getChatId());
            if (request.getLink().isBlank()) {
                throw new IllegalArgumentException("link must not be blank");
            }
            var response = scrapperLinkService.removeLink(
                    request.getChatId(),
                    new backend.academy.linktracker.scrapper.api.dto.RemoveLinkRequest(request.getLink()));
            return toGrpcLinkResponse(response);
        });
    }

    private void validateChatId(long chatId) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
    }

    private LinkResponse toGrpcLinkResponse(backend.academy.linktracker.scrapper.api.dto.LinkResponse response) {
        return LinkResponse.newBuilder()
                .setId(response.id())
                .setUrl(response.url())
                .addAllTags(response.tags())
                .addAllFilters(response.filters())
                .build();
    }

    private <T> void handle(StreamObserver<T> responseObserver, GrpcAction<T> action) {
        try {
            var result = action.run();
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (ChatNotFoundException | LinkNotFoundException exception) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
        } catch (ChatAlreadyExistsException | LinkAlreadyTrackedException exception) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (UnsupportedLinkException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Scrapper gRPC error").asRuntimeException());
        }
    }

    @FunctionalInterface
    private interface GrpcAction<T> {
        T run();
    }
}
