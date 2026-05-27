package backend.academy.linktracker.scrapper.client.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class StackoverflowExternalLinkClientTest {

    private WireMockServer server;
    private StackoverflowExternalLinkClient client;

    @BeforeEach
    void setup() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();

        var properties = new StackoverflowProperties();
        properties.setBaseUrl(server.baseUrl());
        properties.setSite("stackoverflow");
        properties.setKey("");
        properties.setAccessToken("");

        var restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
        client = new StackoverflowExternalLinkClient(restClient, properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void fetchUpdatesReturnsDetectedAnswerWhenResponseIsValid() {
        stubJson("/2.3/questions/12345", "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}");
        stubJson("/2.3/questions/12345/answers", """
                    {"items":[{"answer_id":11,"creation_date":1735732800,"body":"<p>Answer body</p>","owner":{"display_name":"Jane"}}]}
                    """);
        stubJson("/2.3/answers/11/comments", "{\"items\":[]}");
        stubJson("/2.3/questions/12345/comments", "{\"items\":[]}");

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
        stubJson("/2.3/questions/12345", "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}");
        stubJson("/2.3/questions/12345/answers", "{\"items\":[]}");
        stubJson("/2.3/answers/11/comments", "{\"items\":[]}");
        stubJson("/2.3/questions/12345/comments", "{\"items\":[]}");

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
        stubJson("/2.3/questions/12345", "{\"items\":[{\"question_id\":12345,\"title\":\"Sample question\"}]}");
        stubJson("/2.3/questions/12345/answers", """
                    {"items":[{"answer_id":11,"creation_date":1735732700,"body":"<p>Old answer</p>","owner":{"display_name":"Jane"}}]}
                    """);
        stubJson("/2.3/answers/11/comments", """
                    {"items":[{"comment_id":77,"creation_date":1735732900,"body":"<p>Answer comment</p>","owner":{"display_name":"John"}}]}
                    """);
        stubJson("/2.3/questions/12345/comments", "{\"items\":[]}");

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
        server.stubFor(get(urlPathEqualTo("/2.3/questions/12345"))
                .willReturn(aResponse().withStatus(502)));

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

    private void stubJson(String path, String body) {
        server.stubFor(
                get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200).withBody(body)));
    }
}
