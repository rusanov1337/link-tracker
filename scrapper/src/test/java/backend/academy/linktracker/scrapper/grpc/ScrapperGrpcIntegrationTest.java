package backend.academy.linktracker.scrapper.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.RegisterChatRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "app.scheduler.enabled=false",
            "app.cache.tracked-links.enabled=false",
            "app.grpc.server.enabled=true",
            "app.grpc.server.port=19091",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(backend.academy.linktracker.scrapper.TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScrapperGrpcIntegrationTest extends DatabaseCleanupSupport {

    @Test
    void addAndListLinksViaGrpc() {
        var channel = newGrpcChannel();
        try {
            var stub = ScrapperServiceGrpc.newBlockingStub(channel);
            stub.registerChat(RegisterChatRequest.newBuilder().setChatId(1L).build());

            var addResponse = stub.addLink(AddLinkRequest.newBuilder()
                    .setChatId(1L)
                    .setLink("https://github.com/user/repo")
                    .addAllTags(List.of("work"))
                    .addAllFilters(List.of("new"))
                    .build());
            assertEquals("https://github.com/user/repo", addResponse.getUrl());
            assertEquals(List.of("work"), addResponse.getTagsList());

            var listResponse =
                    stub.listLinks(ListLinksRequest.newBuilder().setChatId(1L).build());
            assertEquals(1, listResponse.getSize());
            assertEquals(1, listResponse.getLinksCount());
            assertEquals(
                    "https://github.com/user/repo", listResponse.getLinks(0).getUrl());
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void invalidGrpcRequestReturnsInvalidArgument() {
        var channel = newGrpcChannel();
        try {
            var stub = ScrapperServiceGrpc.newBlockingStub(channel);

            var exception = assertThrows(
                    StatusRuntimeException.class,
                    () -> stub.registerChat(
                            RegisterChatRequest.newBuilder().setChatId(0L).build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
        } finally {
            channel.shutdownNow();
        }
    }

    private ManagedChannel newGrpcChannel() {
        return ManagedChannelBuilder.forAddress("localhost", 19091)
                .usePlaintext()
                .build();
    }
}
