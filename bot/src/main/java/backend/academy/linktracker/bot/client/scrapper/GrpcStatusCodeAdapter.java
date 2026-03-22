package backend.academy.linktracker.bot.client.scrapper;

import io.grpc.Status;

final class GrpcStatusCodeAdapter {

    private GrpcStatusCodeAdapter() {}

    static int toStatusCode(Status status) {
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> 400;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS -> 409;
            default -> 503;
        };
    }
}
