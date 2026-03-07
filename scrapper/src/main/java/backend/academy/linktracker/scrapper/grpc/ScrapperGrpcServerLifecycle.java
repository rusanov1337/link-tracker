package backend.academy.linktracker.scrapper.grpc;

import backend.academy.linktracker.scrapper.properties.GrpcServerProperties;
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
public class ScrapperGrpcServerLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScrapperGrpcServerLifecycle.class);

    private final GrpcServerProperties grpcServerProperties;
    private final ScrapperGrpcService scrapperGrpcService;
    private Server server;

    public ScrapperGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties, ScrapperGrpcService scrapperGrpcService) {
        this.grpcServerProperties = grpcServerProperties;
        this.scrapperGrpcService = scrapperGrpcService;
    }

    @PostConstruct
    void start() {
        try {
            server = ServerBuilder.forPort(grpcServerProperties.getPort())
                    .addService(scrapperGrpcService)
                    .build()
                    .start();
            LOGGER.atInfo()
                    .addKeyValue("transport", "grpc")
                    .addKeyValue("port", grpcServerProperties.getPort())
                    .log("Scrapper gRPC server started");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start scrapper gRPC server", exception);
        }
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.shutdownNow();
            LOGGER.atInfo().addKeyValue("transport", "grpc").log("Scrapper gRPC server stopped");
        }
    }
}
