package backend.academy.linktracker.bot.client.scrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.bot.properties.ResilienceProperties;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.bot.service.BotMetricsService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HttpScrapperClientTest {

    private HttpServer server;
    private HttpScrapperClient client;
    private ResilienceProperties resilienceProperties;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        var scrapperProperties = scrapperProperties();
        resilienceProperties = new ResilienceProperties();
        resilienceProperties.getRetry().setBackoff(Duration.ofMillis(10));
        client = new HttpScrapperClient(
                restClient(scrapperProperties), new HttpResilienceExecutor(resilienceProperties), botMetricsService());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void getLinksRetriesRetryableStatus() {
        var calls = new AtomicInteger();
        server.createContext("/links", exchange -> {
            var call = calls.incrementAndGet();
            if (call < 3) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }

            var payload = "{\"links\":[],\"size\":0}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var response = client.getLinks(1L);

        assertEquals(0, response.size());
        assertEquals(3, calls.get());
    }

    @Test
    void getLinksDoesNotRetryNonRetryableStatus() {
        var calls = new AtomicInteger();
        server.createContext("/links", exchange -> {
            calls.incrementAndGet();
            var payload =
                    "{\"description\":\"bad request\",\"code\":\"400\",\"exceptionName\":\"BadRequestException\",\"exceptionMessage\":\"bad\",\"stacktrace\":[]}";
            var bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, bytes.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });

        var exception = assertThrows(ScrapperClientException.class, () -> client.getLinks(1L));

        assertEquals(400, exception.statusCode());
        assertEquals(1, calls.get());
    }

    @Test
    void getLinksFailsByReadTimeoutBeforeScrapperResponds() throws IOException {
        tearDown();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/links", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        var scrapperProperties = scrapperProperties();
        scrapperProperties.getHttp().setReadTimeout(Duration.ofMillis(100));
        var timeoutClient = new HttpScrapperClient(
                restClient(scrapperProperties), new HttpResilienceExecutor(resilienceProperties), botMetricsService());

        var startedAt = System.nanoTime();
        assertThrows(ScrapperClientException.class, () -> timeoutClient.getLinks(1L));
        var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertTrue(elapsed.compareTo(Duration.ofMillis(500)) < 0);
    }

    @Test
    void getLinksDoesNotCallScrapperWhenCircuitBreakerIsOpen() {
        var calls = new AtomicInteger();
        server.createContext("/links", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        resilienceProperties.getRetry().setMaxAttempts(1);
        resilienceProperties.getCircuitBreaker().setSlidingWindowSize(2);
        resilienceProperties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        resilienceProperties.getCircuitBreaker().setFailureRateThreshold(50.0F);

        assertThrows(ScrapperClientException.class, () -> client.getLinks(1L));
        assertThrows(ScrapperClientException.class, () -> client.getLinks(1L));
        assertThrows(ScrapperClientException.class, () -> client.getLinks(1L));

        assertEquals(2, calls.get());
    }

    private ScrapperProperties scrapperProperties() {
        var properties = new ScrapperProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return properties;
    }

    private RestClient restClient(ScrapperProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getHttp().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getHttp().getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private BotMetricsService botMetricsService() {
        return new BotMetricsService(new SimpleMeterRegistry());
    }
}
