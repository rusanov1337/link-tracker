package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.grpc.BotUpdatesServiceGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.properties.BotProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "grpc")
public class GrpcBotUpdatesClient implements BotUpdatesClient {

    private final ManagedChannel channel;
    private final BotUpdatesServiceGrpc.BotUpdatesServiceBlockingStub blockingStub;

    public GrpcBotUpdatesClient(BotProperties botProperties) {
        this.channel = ManagedChannelBuilder.forAddress(
                        botProperties.getGrpc().getHost(),
                        botProperties.getGrpc().getPort())
                .usePlaintext()
                .build();
        this.blockingStub = BotUpdatesServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        try {
            var request = LinkUpdateRequest.newBuilder()
                    .setId(id)
                    .setUrl(url.toString())
                    .setDescription(description)
                    .addAllTgChatIds(tgChatIds)
                    .build();
            blockingStub.processUpdate(request);
        } catch (StatusRuntimeException exception) {
            throw new BotUpdatesClientException(
                    "Bot gRPC updates endpoint returned status "
                            + exception.getStatus().getCode(),
                    exception);
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
}
