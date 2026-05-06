package backend.academy.linktracker.scrapper.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.scheduler.enabled=false",
            "app.cache.tracked-links.enabled=false",
            "app.rate-limiting.enabled=true",
            "app.rate-limiting.limit-for-period=1",
            "app.rate-limiting.limit-refresh-period=1m",
            "app.rate-limiting.timeout-duration=0ms",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        var firstResponse = sendInvalidListRequest();
        var secondResponse = sendInvalidListRequest();

        assertEquals(400, firstResponse.statusCode());
        assertEquals(429, secondResponse.statusCode());
    }

    private HttpResponse<String> sendInvalidListRequest() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/links"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
