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
import io.grpc.Metadata;
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

    private static final Metadata.Key<String> ERROR_CODE_METADATA_KEY =
            Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final ScrapperServiceGrpc.ScrapperServiceBlockingStub blockingStub;

    public GrpcScrapperClient(ScrapperProperties scrapperProperties) {
        this.channel = ManagedChannelBuilder.forAddress(
                        scrapperProperties.getGrpc().getHost(),
                        scrapperProperties.getGrpc().getPort())
                .usePlaintext()
                .build();
        this.blockingStub = ScrapperServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(scrapperProperties.getGrpc().getDeadline().toMillis(), TimeUnit.MILLISECONDS);
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
            throw toScrapperClientException(exception);
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
            throw toScrapperClientException(exception);
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
            throw toScrapperClientException(exception);
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
            throw toScrapperClientException(exception);
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

    private ScrapperClientException toScrapperClientException(StatusRuntimeException exception) {
        var description = exception.getStatus().getDescription();
        if (description == null || description.isBlank()) {
            description = "Scrapper gRPC error";
        }
        return new ScrapperClientException(
                GrpcStatusCodeAdapter.toStatusCode(exception.getStatus()),
                resolveErrorCode(exception),
                description,
                exception);
    }

    private String resolveErrorCode(StatusRuntimeException exception) {
        var trailers = Status.trailersFromThrowable(exception);
        if (trailers == null) {
            return null;
        }

        return trailers.get(ERROR_CODE_METADATA_KEY);
    }
}
