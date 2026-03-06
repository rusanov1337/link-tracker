package backend.academy.linktracker.scrapper.client.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        var restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
        client = new StackoverflowExternalLinkClient(restClient, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchLastUpdatedReturnsTimestampWhenResponseIsValid() {
        server.createContext("/2.3/questions/12345", exchange -> {
            var payload = "{\"items\":[{\"last_activity_date\":1735732800}]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var result = client.fetchLastUpdated(URI.create("https://stackoverflow.com/questions/12345/sample-question"));

        assertTrue(result.isPresent());
        assertEquals(Instant.ofEpochSecond(1735732800L), result.orElseThrow());
    }

    @Test
    void fetchLastUpdatedReturnsEmptyWhenBodyDoesNotMatchSchema() {
        server.createContext("/2.3/questions/12345", exchange -> {
            var payload = "{\"items\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var result = client.fetchLastUpdated(URI.create("https://stackoverflow.com/questions/12345/sample-question"));

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchLastUpdatedReturnsEmptyWhenProviderReturnsError() {
        server.createContext("/2.3/questions/12345", exchange -> {
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });

        var result = client.fetchLastUpdated(URI.create("https://stackoverflow.com/questions/12345/sample-question"));

        assertTrue(result.isEmpty());
    }
}
