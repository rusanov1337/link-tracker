package backend.academy.linktracker.bot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.telegram.polling-enabled=false",
            "app.telegram.set-my-commands-enabled=false",
            "app.telegram.url=http://localhost/bot",
            "app.telegram.token=test-token",
            "app.scrapper.base-url=http://localhost:8081",
            "app.kafka.consumer.enabled=false",
            "app.rate-limiting.enabled=true",
            "app.rate-limiting.limit-for-period=1",
            "app.rate-limiting.limit-refresh-period=1m",
            "app.rate-limiting.timeout-duration=0ms"
        })
class RateLimitingIntegrationTest {

    @LocalServerPort
    private int port;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void returnsTooManyRequestsWhenIpLimitIsExceeded() throws Exception {
        var firstResponse = sendInvalidUpdateRequest();
        var secondResponse = sendInvalidUpdateRequest();

        assertEquals(400, firstResponse.statusCode());
        assertEquals(429, secondResponse.statusCode());
    }

    private HttpResponse<String> sendInvalidUpdateRequest() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/updates"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
