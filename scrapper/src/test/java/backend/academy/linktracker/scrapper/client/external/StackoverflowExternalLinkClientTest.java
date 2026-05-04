package backend.academy.linktracker.scrapper.client.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class StackoverflowExternalLinkClientTest {

    private HttpServer server;
    private StackoverflowExternalLinkClient client;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        var properties = new StackoverflowProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setSite("stackoverflow");
        properties.setKey("");
        properties.setAccessToken("");

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        var restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        client = new StackoverflowExternalLinkClient(
                restClient, properties, new HttpResilienceExecutor(new ResilienceProperties()));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchUpdatesReturnsDetectedAnswerWhenResponseIsValid() {
        server.createContext("/2.3/questions/12345", exchange -> {
            var payload = "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/answers", exchange -> {
            var payload = """
                    {"items":[{"answer_id":11,"creation_date":1735732800,"body":"<p>Answer body</p>","owner":{"display_name":"Jane"}}]}
                    """;
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/answers/11/comments", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/comments", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                1L,
                URI.create("https://stackoverflow.com/questions/12345/sample-question"),
                Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertEquals(1, result.updates().size());
        assertEquals(UpdateEventType.ANSWER, result.updates().getFirst().eventType());
        assertEquals("Sample question", result.updates().getFirst().title());
        assertEquals("Jane", result.updates().getFirst().author());
        assertEquals("Answer body", result.updates().getFirst().preview());
        assertEquals("answer:11", result.updates().getFirst().cursor());
    }

    @Test
    void fetchUpdatesReturnsEmptyWhenNoNewEventsWereFound() {
        server.createContext("/2.3/questions/12345", exchange -> {
            var payload = "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/answers", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/answers/11/comments", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/comments", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                1L,
                URI.create("https://stackoverflow.com/questions/12345/sample-question"),
                Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.updates().isEmpty());
        assertFalse(result.failed());
    }

    @Test
    void fetchUpdatesReturnsDetectedAnswerCommentWhenCommentIsNew() {
        server.createContext("/2.3/questions/12345", exchange -> {
            var payload = "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/answers", exchange -> {
            var payload = """
                    {"items":[{"answer_id":11,"creation_date":1735732700,"body":"<p>Old answer</p>","owner":{"display_name":"Jane"}}]}
                    """;
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/answers/11/comments", exchange -> {
            var payload = """
                    {"items":[{"comment_id":77,"creation_date":1735732900,"body":"<p>Answer comment</p>","owner":{"display_name":"John"}}]}
                    """;
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
        server.createContext("/2.3/questions/12345/comments", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var trackedLink = TrackedLink.create(
                        1L,
                        URI.create("https://stackoverflow.com/questions/12345/sample-question"),
                        Instant.parse("2025-01-01T00:00:00Z"))
                .withLastUpdatedAt(Instant.ofEpochSecond(1735732800L));
        var result = client.fetchUpdates(trackedLink);

        assertEquals(1, result.updates().size());
        assertEquals(UpdateEventType.COMMENT, result.updates().getFirst().eventType());
        assertEquals("Sample question", result.updates().getFirst().title());
        assertEquals("John", result.updates().getFirst().author());
        assertEquals("Answer comment", result.updates().getFirst().preview());
        assertEquals("answer-comment:77", result.updates().getFirst().cursor());
    }

    @Test
    void fetchUpdatesReturnsEmptyWhenProviderReturnsError() {
        server.createContext("/2.3/questions/12345", exchange -> {
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });

        var trackedLink = TrackedLink.create(
                1L,
                URI.create("https://stackoverflow.com/questions/12345/sample-question"),
                Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.failed());
    }

    @Test
    void supportsOnlyQuestionUrls() {
        assertTrue(client.supports(URI.create("https://stackoverflow.com/questions/12345/sample-question")));
        assertTrue(client.supports(URI.create("https://stackoverflow.com/q/12345")));
        assertTrue(client.supports(URI.create("https://stackoverflow.com/questions/12345/sample-question?sort=votes")));
        assertFalse(client.supports(URI.create("https://stackoverflow.com/users/12345/example")));
        assertFalse(client.supports(URI.create("https://stackoverflow.com/questions/not-a-number/example")));
    }
}
