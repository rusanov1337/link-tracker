package backend.academy.linktracker.scrapper.client.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GithubExternalLinkClientTest {

    private HttpServer server;
    private GithubExternalLinkClient client;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        var properties = new GithubProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setToken("");

        var restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
        client = new GithubExternalLinkClient(restClient, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchUpdatesReturnsDetectedIssueWhenResponseIsValid() {
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            var payload = """
                    [
                      {
                        "id": 101,
                        "title": "New issue",
                        "created_at": "2025-01-01T12:00:00Z",
                        "body_text": "Issue body preview",
                        "user": {"login": "octocat"}
                      }
                    ]
                    """;
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertEquals(1, result.updates().size());
        assertEquals(UpdateEventType.ISSUE, result.updates().getFirst().eventType());
        assertEquals("New issue", result.updates().getFirst().title());
        assertEquals("octocat", result.updates().getFirst().author());
        assertEquals("Issue body preview", result.updates().getFirst().preview());
        assertEquals("101", result.updates().getFirst().cursor());
        assertEquals(Instant.parse("2025-01-01T12:00:00Z"), result.updates().getFirst().createdAt());
    }

    @Test
    void fetchUpdatesReturnsEmptyWhenProviderReturnsError() {
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.updates().isEmpty());
    }

    @Test
    void fetchUpdatesReturnsEmptyWhenBodyIsMalformed() {
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            var payload = "[{\"id\":";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.updates().isEmpty());
    }

    @Test
    void supportsOnlyRepositoryUrls() {
        assertTrue(client.supports(URI.create("https://github.com/octocat/hello-world")));
        assertTrue(client.supports(URI.create("https://github.com/octocat/hello-world/")));
        assertTrue(client.supports(URI.create("https://github.com/octocat/hello-world?tab=readme")));
        assertTrue(client.supports(URI.create("https://github.com/octocat/hello-world#top")));
        assertTrue(client.supports(URI.create("https://GITHUB.COM/octocat/hello-world")));
        assertFalse(client.supports(URI.create("https://github.com/octocat/hello-world/issues/1")));
        assertFalse(client.supports(URI.create("https://github.com/octocat")));
    }
}
