package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
@TestPropertySource(properties = "app.telegram.polling-enabled=true")
class CommandEdgeCasesIntegrationTest {

    @Test
    void startCommandWithMentionReturnsWelcomeMessage() {
        int updateId = 223001;
        stubMessageUpdateScenario("start-command-mention", updateId, "/start@test_bot");
        stubSendMessageSuccess();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(
                        1,
                        postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                                .withRequestBody(containing("chat_id=987654321"))
                                .withRequestBody(containing("%2Fhelp"))));
    }

    @Test
    void startCommandWithPayloadReturnsWelcomeMessage() {
        int updateId = 223002;
        stubMessageUpdateScenario("start-command-payload", updateId, "/start payload");
        stubSendMessageSuccess();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(
                        1,
                        postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                                .withRequestBody(containing("chat_id=987654321"))
                                .withRequestBody(containing("%2Fhelp"))));
    }

    @Test
    void nonCommandTextDoesNotProduceSendMessage() {
        int updateId = 223003;
        stubMessageUpdateScenario("plain-text-message", updateId, "hello world");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(postRequestedFor(urlMatching("/bot[^/]+/getUpdates"))
                        .withRequestBody(containing("offset=" + (updateId + 1)))));

        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void callbackQueryUpdateDoesNotProduceSendMessage() {
        int updateId = 223004;
        stubCallbackUpdateScenario("callback-query-update", updateId);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(postRequestedFor(urlMatching("/bot[^/]+/getUpdates"))
                        .withRequestBody(containing("offset=" + (updateId + 1)))));

        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    private void stubMessageUpdateScenario(String scenario, int updateId, String text) {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": %d,
                                      "message": {
                                        "message_id": 10,
                                        "from": {
                                          "id": 123456789,
                                          "is_bot": false,
                                          "first_name": "Test"
                                        },
                                        "chat": {
                                          "id": 987654321,
                                          "type": "private"
                                        },
                                        "date": 1234567890,
                                        "text": "%s"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(updateId, text)))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenario)
                .whenScenarioStateIs("consumed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));
    }

    private void stubCallbackUpdateScenario(String scenario, int updateId) {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": %d,
                                      "callback_query": {
                                        "id": "callback-1",
                                        "from": {
                                          "id": 123456789,
                                          "is_bot": false,
                                          "first_name": "Test"
                                        },
                                        "message": {
                                          "message_id": 99,
                                          "chat": {
                                            "id": 987654321,
                                            "type": "private"
                                          },
                                          "date": 1234567890,
                                          "text": "button"
                                        },
                                        "data": "track"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(updateId)))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenario)
                .whenScenarioStateIs("consumed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));
    }

    private void stubSendMessageSuccess() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 11
                                  }
                                }
                                """)));
    }
}
