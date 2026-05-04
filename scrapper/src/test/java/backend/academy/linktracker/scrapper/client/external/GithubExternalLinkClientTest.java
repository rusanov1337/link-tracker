package backend.academy.linktracker.scrapper.client.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class GithubExternalLinkClientTest {

    private HttpServer server;
    private GithubExternalLinkClient client;
    private ResilienceProperties resilienceProperties;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        var properties = new GithubProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setToken("");

        var restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
        resilienceProperties = new ResilienceProperties();
        resilienceProperties.getRetry().setBackoff(Duration.ofMillis(10));
        client = new GithubExternalLinkClient(restClient, properties, new HttpResilienceExecutor(resilienceProperties));
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
        assertEquals(
                Instant.parse("2025-01-01T12:00:00Z"),
                result.updates().getFirst().createdAt());
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

        assertTrue(result.failed());
    }

    @Test
    void fetchUpdatesRetriesRetryableStatus() {
        var calls = new AtomicInteger();
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            var call = calls.incrementAndGet();
            if (call < 3) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }

            var payload = "[]";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertFalse(result.failed());
        assertEquals(3, calls.get());
    }

    @Test
    void fetchUpdatesDoesNotRetryNonRetryableStatus() {
        var calls = new AtomicInteger();
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.failed());
        assertEquals(1, calls.get());
    }

    @Test
    void fetchUpdatesFailsByReadTimeoutBeforeProviderResponds() throws IOException {
        tearDown();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        var properties = new GithubProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setReadTimeout(Duration.ofMillis(100));

        var restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
        var timeoutClient =
                new GithubExternalLinkClient(restClient, properties, new HttpResilienceExecutor(resilienceProperties));

        var startedAt = System.nanoTime();
        var result = timeoutClient.fetchUpdates(TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z")));
        var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertTrue(result.failed());
        assertTrue(elapsed.compareTo(Duration.ofMillis(500)) < 0);
    }

    private SimpleClientHttpRequestFactory requestFactory(GithubProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return requestFactory;
    }

    @Test
    void fetchUpdatesDoesNotCallProviderWhenCircuitBreakerIsOpen() {
        var calls = new AtomicInteger();
        server.createContext("/repos/octocat/hello-world/issues", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        resilienceProperties.getRetry().setMaxAttempts(1);
        resilienceProperties.getCircuitBreaker().setSlidingWindowSize(2);
        resilienceProperties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        resilienceProperties.getCircuitBreaker().setFailureRateThreshold(50.0F);

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));

        assertTrue(client.fetchUpdates(trackedLink).failed());
        assertTrue(client.fetchUpdates(trackedLink).failed());
        assertTrue(client.fetchUpdates(trackedLink).failed());

        assertEquals(2, calls.get());
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

        assertTrue(result.failed());
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
