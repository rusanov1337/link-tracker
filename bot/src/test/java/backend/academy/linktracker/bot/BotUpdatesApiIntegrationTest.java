package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.service.PendingLinkUpdateStore;
import backend.academy.linktracker.bot.service.RecentlyDeliveredLinkUpdateStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableWireMock
@TestPropertySource(properties = {"app.telegram.polling-enabled=false", "app.telegram.set-my-commands-enabled=false"})
class BotUpdatesApiIntegrationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private PendingLinkUpdateStore pendingLinkUpdateStore;

    @org.springframework.beans.factory.annotation.Autowired
    private RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        recentlyDeliveredLinkUpdateStore.clear();
    }

    @Test
    void validUpdatesRequestReturnsOk() throws Exception {
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

        var requestBody = """
                {
                  "id": 1,
                  "url": "https://github.com/user/repo",
                  "description": "New update",
                  "tgChatIds": [111, 222]
                }
                """;
        var response = sendUpdatesRequest(requestBody);

        assertEquals(200, response.statusCode());
        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void invalidUpdatesRequestReturnsBadRequest() throws Exception {
        var invalidBody = """
                {
                  "id": 1,
                  "url": "not-url"
                }
                """;

        var response = sendUpdatesRequest(invalidBody);

        assertEquals(400, response.statusCode());
    }

    @Test
    void missingRequiredFieldsReturnsBadRequest() throws Exception {
        var invalidBody = """
                {
                  "id": 1,
                  "url": "https://github.com/user/repo"
                }
                """;

        var response = sendUpdatesRequest(invalidBody);

        assertEquals(400, response.statusCode());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        var invalidJson = """
                {
                  "id": 1,
                  "url": "https://github.com/user/repo",
                  "description": "oops",
                  "tgChatIds": [1, 2
                }
                """;

        var response = sendUpdatesRequest(invalidJson);

        assertEquals(400, response.statusCode());
    }

    @Test
    void telegramDeliveryFailureQueuesPendingRetriesAndReturnsOk() throws Exception {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse().withStatus(500)));

        var requestBody = """
                {
                  "id": 1,
                  "url": "https://github.com/user/repo",
                  "description": "New update",
                  "tgChatIds": [111, 222]
                }
                """;
        var response = sendUpdatesRequest(requestBody);

        assertEquals(200, response.statusCode());
        assertEquals(2, pendingLinkUpdateStore.size());
        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void validReportsRequestReturnsOk() throws Exception {
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

        var requestBody = """
                {
                  "description": "Failed links report",
                  "tgChatIds": [111, 222]
                }
                """;
        var response = sendReportsRequest(requestBody);

        assertEquals(200, response.statusCode());
        verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    private HttpResponse<String> sendUpdatesRequest(String jsonBody) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/updates"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendReportsRequest(String jsonBody) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/reports"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
