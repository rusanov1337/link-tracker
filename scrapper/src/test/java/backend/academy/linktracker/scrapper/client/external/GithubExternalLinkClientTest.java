package backend.academy.linktracker.scrapper.client.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class GithubExternalLinkClientTest {

    private WireMockServer server;
    private GithubExternalLinkClient client;
    private ResilienceProperties resilienceProperties;

    @BeforeEach
    void setup() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();

        var properties = new GithubProperties();
        properties.setBaseUrl(server.baseUrl());
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
            server.stop();
        }
    }

    @Test
    void fetchUpdatesReturnsDetectedIssueWhenResponseIsValid() {
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(200).withBody("""
                    [
                      {
                        "id": 101,
                        "title": "New issue",
                        "created_at": "2025-01-01T12:00:00Z",
                        "body_text": "Issue body preview",
                        "user": {"login": "octocat"}
                      }
                    ]
                    """)));

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
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(503)));

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.failed());
    }

    @Test
    void fetchUpdatesRetriesRetryableStatus() {
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("third-attempt"));
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs("third-attempt")
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertFalse(result.failed());
        server.verify(3, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/issues")));
    }

    @Test
    void fetchUpdatesDoesNotRetryNonRetryableStatus() {
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(400)));

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));
        var result = client.fetchUpdates(trackedLink);

        assertTrue(result.failed());
        server.verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/issues")));
    }

    @Test
    void fetchUpdatesFailsByReadTimeoutBeforeProviderResponds() {
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        var properties = new GithubProperties();
        properties.setBaseUrl(server.baseUrl());
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
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(500)));
        resilienceProperties.getRetry().setMaxAttempts(1);
        resilienceProperties.getCircuitBreaker().setSlidingWindowSize(2);
        resilienceProperties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        resilienceProperties.getCircuitBreaker().setFailureRateThreshold(50.0F);

        var trackedLink = TrackedLink.create(
                1L, URI.create("https://github.com/octocat/hello-world"), Instant.parse("2025-01-01T00:00:00Z"));

        assertTrue(client.fetchUpdates(trackedLink).failed());
        assertTrue(client.fetchUpdates(trackedLink).failed());
        assertTrue(client.fetchUpdates(trackedLink).failed());

        server.verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/hello-world/issues")));
    }

    @Test
    void fetchUpdatesReturnsEmptyWhenBodyIsMalformed() {
        server.stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"id\":")));

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
