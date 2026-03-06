package backend.academy.linktracker.scrapper.client.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void fetchLastUpdatedReturnsTimestampWhenResponseIsValid() {
        server.createContext("/repos/octocat/hello-world", exchange -> {
            var payload = "{\"updated_at\":\"2025-01-01T12:00:00Z\"}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var result = client.fetchLastUpdated(URI.create("https://github.com/octocat/hello-world"));

        assertTrue(result.isPresent());
        assertEquals(Instant.parse("2025-01-01T12:00:00Z"), result.orElseThrow());
    }

    @Test
    void fetchLastUpdatedReturnsEmptyWhenProviderReturnsError() {
        server.createContext("/repos/octocat/hello-world", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        var result = client.fetchLastUpdated(URI.create("https://github.com/octocat/hello-world"));

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchLastUpdatedReturnsEmptyWhenBodyIsMalformed() {
        server.createContext("/repos/octocat/hello-world", exchange -> {
            var payload = "{\"updated_at\":";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var result = client.fetchLastUpdated(URI.create("https://github.com/octocat/hello-world"));

        assertTrue(result.isEmpty());
    }
}
