package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.bot.service.PendingFailureReportStore;
import backend.academy.linktracker.bot.service.PendingLinkUpdateStore;
import backend.academy.linktracker.bot.service.RecentlyDeliveredLinkUpdateStore;
import backend.academy.linktracker.grpc.BotUpdatesServiceGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.grpc.ProcessingFailureReportRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock
@TestPropertySource(
        properties = {
            "app.telegram.polling-enabled=false",
            "app.telegram.set-my-commands-enabled=false",
            "app.grpc.server.enabled=true",
            "app.grpc.server.port=19090"
        })
class BotUpdatesGrpcIntegrationTest {

    @Autowired
    private PendingLinkUpdateStore pendingLinkUpdateStore;

    @Autowired
    private PendingFailureReportStore pendingFailureReportStore;

    @Autowired
    private RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore;

    @BeforeEach
    void setUp() {
        recentlyDeliveredLinkUpdateStore.clear();
    }

    @Test
    void validGrpcUpdateRequestSendsTelegramMessages() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 100
                                  }
                                }
                                """)));

        var channel = newGrpcChannel();
        try {
            var request = LinkUpdateRequest.newBuilder()
                    .setId(1)
                    .setUrl("https://github.com/user/repo")
                    .setDescription("New update")
                    .addAllTgChatIds(List.of(111L, 222L))
                    .build();

            BotUpdatesServiceGrpc.newBlockingStub(channel).processUpdate(request);
        } finally {
            channel.shutdownNow();
        }

        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void invalidGrpcUpdateUrlReturnsInvalidArgument() {
        var channel = newGrpcChannel();
        try {
            var request = LinkUpdateRequest.newBuilder()
                    .setId(1)
                    .setUrl("not-url")
                    .setDescription("New update")
                    .addTgChatIds(111L)
                    .build();

            var exception =
                    assertThrows(StatusRuntimeException.class, () -> BotUpdatesServiceGrpc.newBlockingStub(channel)
                            .processUpdate(request));

            assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
        } finally {
            channel.shutdownNow();
        }

        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void telegramDeliveryFailureQueuesPendingRetriesAndReturnsOk() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse().withStatus(500)));

        var channel = newGrpcChannel();
        try {
            var request = LinkUpdateRequest.newBuilder()
                    .setId(1)
                    .setUrl("https://github.com/user/repo")
                    .setDescription("New update")
                    .addAllTgChatIds(List.of(111L, 222L))
                    .build();

            BotUpdatesServiceGrpc.newBlockingStub(channel).processUpdate(request);
        } finally {
            channel.shutdownNow();
        }

        assertEquals(2, pendingLinkUpdateStore.size());
        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void validGrpcFailureReportSendsTelegramMessages() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 100
                                  }
                                }
                                """)));

        var channel = newGrpcChannel();
        try {
            var request = ProcessingFailureReportRequest.newBuilder()
                    .setDescription("Failed links")
                    .addAllTgChatIds(List.of(111L, 222L))
                    .build();

            BotUpdatesServiceGrpc.newBlockingStub(channel).processReport(request);
        } finally {
            channel.shutdownNow();
        }

        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void telegramDeliveryFailureQueuesPendingGrpcReportsAndReturnsOk() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse().withStatus(500)));

        var channel = newGrpcChannel();
        try {
            var request = ProcessingFailureReportRequest.newBuilder()
                    .setDescription("Failed links")
                    .addAllTgChatIds(List.of(111L, 222L))
                    .build();

            BotUpdatesServiceGrpc.newBlockingStub(channel).processReport(request);
        } finally {
            channel.shutdownNow();
        }

        assertEquals(2, pendingFailureReportStore.size());
        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    private ManagedChannel newGrpcChannel() {
        return ManagedChannelBuilder.forAddress("localhost", 19090)
                .usePlaintext()
                .build();
    }
}
