package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.properties.GrpcServerProperties;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.grpc.server", name = "enabled", havingValue = "true")
public class BotGrpcServerLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotGrpcServerLifecycle.class);

    private final GrpcServerProperties grpcServerProperties;
    private final BotUpdatesGrpcService botUpdatesGrpcService;
    private Server server;

    public BotGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties, BotUpdatesGrpcService botUpdatesGrpcService) {
        this.grpcServerProperties = grpcServerProperties;
        this.botUpdatesGrpcService = botUpdatesGrpcService;
    }

    @PostConstruct
    void start() {
        try {
            server = ServerBuilder.forPort(grpcServerProperties.getPort())
                    .addService(botUpdatesGrpcService)
                    .build()
                    .start();
            LOGGER.atInfo()
                    .addKeyValue("transport", "grpc")
                    .addKeyValue("port", grpcServerProperties.getPort())
                    .log("Bot gRPC server started");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start bot gRPC server", exception);
        }
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.shutdownNow();
            LOGGER.atInfo().addKeyValue("transport", "grpc").log("Bot gRPC server stopped");
        }
    }
}
