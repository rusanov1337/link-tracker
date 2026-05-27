package backend.academy.linktracker.scrapper.client.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.web.client.RestClient;

class HttpBotUpdatesClientTest {

    private WireMockServer server;
    private HttpBotUpdatesClient client;

    @BeforeEach
    void setup() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();

        var restClient = RestClient.builder().baseUrl(server.baseUrl()).build();
        client = new HttpBotUpdatesClient(restClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void sendLinkUpdatePostsRequestWhenBotReturnsOk() {
        server.stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        assertDoesNotThrow(() -> client.sendLinkUpdate(
                42L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L, 2L)));
        var requestBody = parseRequestBody(server.findAll(postRequestedFor(urlEqualTo("/updates")))
                .getFirst()
                .getBodyAsString());
        assertTrue(requestBody.get("id") instanceof Number id && id.longValue() == 42L);
        assertEquals("https://github.com/octocat/hello-world", requestBody.get("url"));
        assertEquals("Updated", requestBody.get("description"));
        assertEquals(List.of(1, 2), requestBody.get("tgChatIds"));
    }

    @Test
    void sendLinkUpdateThrowsWhenBotReturnsNon2xx() {
        server.stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(400)));

        var exception = assertThrows(
                BotUpdatesClientException.class,
                () -> client.sendLinkUpdate(
                        1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));
        assertTrue(exception.getMessage().contains("400"));
    }

    @Test
    void sendLinkUpdateThrowsWhenBotIsUnavailable() {
        var unavailableClient = new HttpBotUpdatesClient(
                RestClient.builder().baseUrl("http://localhost:1").build());

        var exception = assertThrows(
                BotUpdatesClientException.class,
                () -> unavailableClient.sendLinkUpdate(
                        1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));
        assertTrue(exception.getMessage().contains("Bot updates endpoint call failed"));
        assertNotNull(exception.getCause());
    }

    @Test
    void sendProcessingFailureReportPostsRequestWhenBotReturnsOk() {
        server.stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        assertDoesNotThrow(() -> client.sendProcessingFailureReport("Failed links", List.of(1L, 2L)));
        var requestBody = parseRequestBody(server.findAll(postRequestedFor(urlEqualTo("/reports")))
                .getFirst()
                .getBodyAsString());
        assertEquals("Failed links", requestBody.get("description"));
        assertEquals(List.of(1, 2), requestBody.get("tgChatIds"));
    }

    private Map<String, Object> parseRequestBody(String requestBody) {
        return new JacksonJsonParser().parseMap(requestBody);
    }
}
