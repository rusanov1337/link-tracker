package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import backend.academy.linktracker.grpc.BotUpdatesServiceGrpc;
import backend.academy.linktracker.grpc.Empty;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.grpc.ProcessingFailureReportRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BotUpdatesGrpcService extends BotUpdatesServiceGrpc.BotUpdatesServiceImplBase {

    private final LinkUpdateNotificationService linkUpdateNotificationService;
    private final Validator validator;

    public BotUpdatesGrpcService(LinkUpdateNotificationService linkUpdateNotificationService, Validator validator) {
        this.linkUpdateNotificationService = linkUpdateNotificationService;
        this.validator = validator;
    }

    @Override
    public void processUpdate(LinkUpdateRequest request, StreamObserver<Empty> responseObserver) {
        try {
            validateRequest(request);
            linkUpdateNotificationService.process(new LinkUpdate(
                    request.getId(), request.getUrl(), request.getDescription(), request.getTgChatIdsList()));
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Failed to process update").asRuntimeException());
        }
    }

    @Override
    public void processReport(ProcessingFailureReportRequest request, StreamObserver<Empty> responseObserver) {
        try {
            validateRequest(request);
            linkUpdateNotificationService.processReport(
                    new ProcessingFailureReport(request.getDescription(), request.getTgChatIdsList()));
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Failed to process report").asRuntimeException());
        }
    }

    private void validateRequest(LinkUpdateRequest request) {
        var linkUpdate =
                new LinkUpdate(request.getId(), request.getUrl(), request.getDescription(), request.getTgChatIdsList());
        validate(linkUpdate);
    }

    private void validateRequest(ProcessingFailureReportRequest request) {
        validate(new ProcessingFailureReport(request.getDescription(), request.getTgChatIdsList()));
    }

    private void validate(Object request) {
        var violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }

        var message = violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        throw new IllegalArgumentException(message);
    }
}
