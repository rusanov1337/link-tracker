package backend.academy.linktracker.scrapper.client.bot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HttpBotUpdatesClientTest {

    private HttpServer server;
    private HttpBotUpdatesClient client;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .requestFactory(requestFactory)
                .build();
        client = new HttpBotUpdatesClient(restClient, new HttpResilienceExecutor(new ResilienceProperties()));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendLinkUpdatePostsRequestWhenBotReturnsOk() {
        var requestBodyRef = new AtomicReference<String>();
        server.createContext("/updates", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodyRef.set(body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertDoesNotThrow(() -> client.sendLinkUpdate(
                42L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L, 2L)));
        var requestBody = parseRequestBody(requestBodyRef.get());
        assertTrue(requestBody.get("id") instanceof Number id && id.longValue() == 42L);
        assertEquals("https://github.com/octocat/hello-world", requestBody.get("url"));
        assertEquals("Updated", requestBody.get("description"));
        assertEquals(List.of(1, 2), requestBody.get("tgChatIds"));
    }

    @Test
    void sendLinkUpdateThrowsWhenBotReturnsNon2xx() {
        server.createContext("/updates", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });

        var exception = assertThrows(
                BotUpdatesClientException.class,
                () -> client.sendLinkUpdate(
                        1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));
        assertTrue(exception.getMessage().contains("400"));
    }

    @Test
    void sendLinkUpdateThrowsWhenBotIsUnavailable() {
        var unavailableClient = new HttpBotUpdatesClient(
                RestClient.builder().baseUrl("http://localhost:1").build(),
                new HttpResilienceExecutor(new ResilienceProperties()));

        var exception = assertThrows(
                BotUpdatesClientException.class,
                () -> unavailableClient.sendLinkUpdate(
                        1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));
        assertTrue(exception.getMessage().contains("Bot updates endpoint call failed"));
        assertNotNull(exception.getCause());
    }

    @Test
    void sendProcessingFailureReportPostsRequestWhenBotReturnsOk() {
        var requestBodyRef = new AtomicReference<String>();
        server.createContext("/reports", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodyRef.set(body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertDoesNotThrow(() -> client.sendProcessingFailureReport("Failed links", List.of(1L, 2L)));
        var requestBody = parseRequestBody(requestBodyRef.get());
        assertEquals("Failed links", requestBody.get("description"));
        assertEquals(List.of(1, 2), requestBody.get("tgChatIds"));
    }

    private Map<String, Object> parseRequestBody(String requestBody) {
        return new JacksonJsonParser().parseMap(requestBody);
    }
}
