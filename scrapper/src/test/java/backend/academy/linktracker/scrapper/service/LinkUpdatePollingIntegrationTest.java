package backend.academy.linktracker.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        properties = {
            "app.scheduler.enabled=false",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(backend.academy.linktracker.scrapper.TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LinkUpdatePollingIntegrationTest extends DatabaseCleanupSupport {

    @Autowired
    private LinkUpdatePollingService linkUpdatePollingService;

    @Autowired
    private ScrapperLinkService scrapperLinkService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void checkUpdatesSendsNotificationToTrackedChats() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .withQueryParam("state", containing("all"))
                .withQueryParam("sort", containing("created"))
                .withQueryParam("direction", containing("desc"))
                .withQueryParam("per_page", containing("100"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                [
                                  {
                                    "id": 101,
                                    "title": "New issue",
                                    "created_at": "2099-01-01T00:00:00Z",
                                    "body_text": "Issue preview",
                                    "user": { "login": "octocat" }
                                  }
                                ]
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.registerChat(2L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));
        scrapperLinkService.addLink(
                2L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(
                1,
                postRequestedFor(urlEqualTo("/updates"))
                        .withRequestBody(containing("\"url\":\"https://github.com/octocat/hello-world\""))
                        .withRequestBody(containing("\"tgChatIds\":[1,2]"))
                        .withRequestBody(containing("New issue"))
                        .withRequestBody(containing("octocat"))
                        .withRequestBody(containing("Issue preview")));
        assertOutboxState(1, 1);
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiFails() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
        verify(
                1,
                postRequestedFor(urlEqualTo("/reports"))
                        .withRequestBody(containing("https://github.com/octocat/hello-world")));
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiBodyIsMalformed() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"id\":")));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
        verify(
                1,
                postRequestedFor(urlEqualTo("/reports"))
                        .withRequestBody(containing("https://github.com/octocat/hello-world")));
    }

    @Test
    void checkUpdatesDoesNotDuplicateNotificationWhenCalledConcurrently() throws Exception {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .withQueryParam("state", containing("all"))
                .withQueryParam("sort", containing("created"))
                .withQueryParam("direction", containing("desc"))
                .withQueryParam("per_page", containing("100"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(200).withBody("""
                                [
                                  {
                                    "id": 101,
                                    "title": "New issue",
                                    "created_at": "2099-01-01T00:00:00Z",
                                    "body_text": "Issue preview",
                                    "user": { "login": "octocat" }
                                  }
                                ]
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var startLatch = new CountDownLatch(1);
            var first = executor.submit(() -> {
                awaitStart(startLatch);
                linkUpdatePollingService.checkUpdates();
            });
            var second = executor.submit(() -> {
                awaitStart(startLatch);
                linkUpdatePollingService.checkUpdates();
            });

            startLatch.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(1, postRequestedFor(urlEqualTo("/updates")));
    }

    @Test
    void checkUpdatesRetriesOutboxNotificationOnNextRunWhenBotWasUnavailable() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .withQueryParam("state", containing("all"))
                .withQueryParam("sort", containing("created"))
                .withQueryParam("direction", containing("desc"))
                .withQueryParam("per_page", containing("100"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                [
                                  {
                                    "id": 101,
                                    "title": "New issue",
                                    "created_at": "2099-01-01T00:00:00Z",
                                    "body_text": "Issue preview",
                                    "user": { "login": "octocat" }
                                  }
                                ]
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(1, postRequestedFor(urlEqualTo("/updates")));
        verify(1, postRequestedFor(urlEqualTo("/reports")));
        assertOutboxState(2, 1);

        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        linkUpdatePollingService.checkUpdates();

        verify(2, postRequestedFor(urlEqualTo("/updates")));
        verify(1, postRequestedFor(urlEqualTo("/reports")));
        assertOutboxState(2, 2);
    }

    @Test
    void checkUpdatesStoresOutboxRecordUntilNotificationIsDelivered() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .withQueryParam("state", containing("all"))
                .withQueryParam("sort", containing("created"))
                .withQueryParam("direction", containing("desc"))
                .withQueryParam("per_page", containing("100"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                [
                                  {
                                    "id": 101,
                                    "title": "New issue",
                                    "created_at": "2099-01-01T00:00:00Z",
                                    "body_text": "Issue preview",
                                    "user": { "login": "octocat" }
                                  }
                                ]
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        var stored = loadOutboxRows();
        org.junit.jupiter.api.Assertions.assertEquals(2, stored.size());
        var linkUpdateRow = findSingleEvent(stored, "LINK_UPDATE");
        var reportRow = findSingleEvent(stored, "PROCESSING_FAILURE_REPORT");
        assertNull(linkUpdateRow.processedAt());
        assertNull(linkUpdateRow.processingOwner());
        assertNull(linkUpdateRow.processingUntil());
        assertNotNull(reportRow.processedAt());
        assertNull(reportRow.processingOwner());
        assertNull(reportRow.processingUntil());

        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        linkUpdatePollingService.checkUpdates();

        stored = loadOutboxRows();
        org.junit.jupiter.api.Assertions.assertEquals(2, stored.size());
        linkUpdateRow = findSingleEvent(stored, "LINK_UPDATE");
        reportRow = findSingleEvent(stored, "PROCESSING_FAILURE_REPORT");
        assertNotNull(linkUpdateRow.processedAt());
        assertNull(linkUpdateRow.processingOwner());
        assertNull(linkUpdateRow.processingUntil());
        assertNotNull(reportRow.processedAt());
        assertNull(reportRow.processingOwner());
        assertNull(reportRow.processingUntil());
    }

    private void assertOutboxState(int totalCount, int processedCount) {
        org.junit.jupiter.api.Assertions.assertEquals(
                totalCount,
                jdbcClient
                        .sql("select count(*) from notification_outbox")
                        .query(Integer.class)
                        .single());
        org.junit.jupiter.api.Assertions.assertEquals(
                processedCount,
                jdbcClient
                        .sql("select count(*) from notification_outbox where processed_at is not null")
                        .query(Integer.class)
                        .single());
    }

    private List<OutboxRow> loadOutboxRows() {
        return jdbcClient
                .sql("""
                        select event_type, processed_at, processing_owner, processing_until
                        from notification_outbox
                        order by id
                        """)
                .query((resultSet, rowNum) -> new OutboxRow(
                        resultSet.getString("event_type"),
                        toInstant(resultSet.getTimestamp("processed_at")),
                        resultSet.getString("processing_owner"),
                        toInstant(resultSet.getTimestamp("processing_until"))))
                .list();
    }

    private OutboxRow findSingleEvent(List<OutboxRow> rows, String eventType) {
        return rows.stream()
                .filter(row -> eventType.equals(row.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing outbox event type " + eventType));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void awaitStart(CountDownLatch startLatch) {
        try {
            assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Timed out while waiting for concurrent start");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent start", exception);
        }
    }

    private record OutboxRow(String eventType, Instant processedAt, String processingOwner, Instant processingUntil) {}
}
