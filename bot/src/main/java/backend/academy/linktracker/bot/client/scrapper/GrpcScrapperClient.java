package backend.academy.linktracker.bot.client.scrapper;

import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.RegisterChatRequest;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "grpc")
public class GrpcScrapperClient implements ScrapperClient {

    private final ManagedChannel channel;
    private final ScrapperServiceGrpc.ScrapperServiceBlockingStub blockingStub;

    public GrpcScrapperClient(ScrapperProperties scrapperProperties) {
        this.channel = ManagedChannelBuilder.forAddress(
                        scrapperProperties.getGrpc().getHost(),
                        scrapperProperties.getGrpc().getPort())
                .usePlaintext()
                .build();
        this.blockingStub = ScrapperServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void ensureChatRegistered(long chatId) {
        try {
            blockingStub.registerChat(
                    RegisterChatRequest.newBuilder().setChatId(chatId).build());
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                return;
            }
            throw toScrapperClientException(exception, "Failed to register chat in scrapper");
        }
    }

    @Override
    public ListLinksResponse getLinks(long chatId) {
        try {
            var response = blockingStub.listLinks(
                    ListLinksRequest.newBuilder().setChatId(chatId).build());
            var links =
                    response.getLinksList().stream().map(this::toLinkResponse).toList();
            return new ListLinksResponse(links, response.getSize());
        } catch (StatusRuntimeException exception) {
            throw toScrapperClientException(exception, "Failed to list links in scrapper");
        }
    }

    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        try {
            var request = AddLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(link)
                    .addAllTags(tags)
                    .addAllFilters(filters)
                    .build();
            return toLinkResponse(blockingStub.addLink(request));
        } catch (StatusRuntimeException exception) {
            throw toScrapperClientException(exception, "Failed to add link in scrapper");
        }
    }

    @Override
    public LinkResponse removeLink(long chatId, String link) {
        try {
            var request = RemoveLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(link)
                    .build();
            return toLinkResponse(blockingStub.removeLink(request));
        } catch (StatusRuntimeException exception) {
            throw toScrapperClientException(exception, "Failed to remove link in scrapper");
        }
    }

    @PreDestroy
    void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    private LinkResponse toLinkResponse(backend.academy.linktracker.grpc.LinkResponse response) {
        return new LinkResponse(response.getId(), response.getUrl(), response.getTagsList(), response.getFiltersList());
    }

    private ScrapperClientException toScrapperClientException(StatusRuntimeException exception, String message) {
        return new ScrapperClientException(toHttpStatusCode(exception.getStatus()), message, exception);
    }

    private int toHttpStatusCode(Status status) {
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> 400;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS -> 409;
            default -> 503;
        };
    }
}
