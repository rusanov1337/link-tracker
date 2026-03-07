package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.service.command.TrackDialogService;
import java.time.Duration;
import java.util.List;
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
class TrackCommandIntegrationTest {

    @Test
    void trackCommandDialogAddsLink() {
        stubDialogUpdates("track-dialog", List.of("/track", "https://github.com/user/repo", "work, docs", "-"), 330001);
        stubFor(post(urlEqualTo("/links"))
                .withHeader("Tg-Chat-Id", containing("987654321"))
                .withRequestBody(containing("\"link\":\"https://github.com/user/repo\""))
                .withRequestBody(containing("\"tags\":[\"work\",\"docs\"]"))
                .withRequestBody(containing("\"filters\":[]"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "id": 100,
                                  "url": "https://github.com/user/repo",
                                  "tags": ["work", "docs"],
                                  "filters": []
                                }
                                """)));
        stubSendMessageSuccess();
        resetAllRequests();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(4, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            verify(0, postRequestedFor(urlEqualTo("/tg-chat/987654321")));
            var requests = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));

            assertEquals(
                    TrackDialogService.START_PROMPT,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(0).getBodyAsString(), "text"));
            assertEquals(
                    TrackDialogService.TAGS_PROMPT,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(1).getBodyAsString(), "text"));
            assertEquals(
                    TrackDialogService.FILTERS_PROMPT,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(2).getBodyAsString(), "text"));
            assertEquals(
                    TrackDialogService.SUCCESS_RESPONSE,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(3).getBodyAsString(), "text"));
        });
    }

    @Test
    void cancelCommandInterruptsTrackDialog() {
        stubDialogUpdates("track-cancel-dialog", List.of("/track", "/cancel"), 331001);
        stubSendMessageSuccess();
        resetAllRequests();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            var requests = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            assertEquals(
                    TrackDialogService.START_PROMPT,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(0).getBodyAsString(), "text"));
            assertEquals(
                    TrackDialogService.CANCELLED_RESPONSE,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(1).getBodyAsString(), "text"));
        });
    }

    @Test
    void anotherCommandCancelsTrackDialogAndStopsFlow() {
        stubDialogUpdates(
                "track-interrupted-by-command", List.of("/track", "/help", "https://github.com/user/repo"), 332001);
        stubSendMessageSuccess();
        resetAllRequests();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            var requests = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));

            assertEquals(
                    TrackDialogService.START_PROMPT,
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(0).getBodyAsString(), "text"));
            assertTrue(
                    TelegramRequestBodyParser.getFormFieldValue(requests.get(1).getBodyAsString(), "text")
                            .startsWith("Доступные команды:"));
            verify(0, postRequestedFor(urlEqualTo("/links")));
            verify(0, postRequestedFor(urlEqualTo("/tg-chat/987654321")));
        });
    }

    private void stubDialogUpdates(String scenario, List<String> messages, int firstUpdateId) {
        int updateId = firstUpdateId;
        String state = STARTED;
        for (int i = 0; i < messages.size(); i++) {
            String nextState = "step-" + i;
            var text = messages.get(i);
            stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(state)
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
                                            "message_id": %d,
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
                                    """.formatted(updateId, 100 + i, text)))
                    .willSetStateTo(nextState));
            state = nextState;
            updateId++;
        }

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenario)
                .whenScenarioStateIs(state)
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
                                    "message_id": 44
                                  }
                                }
                                """)));
    }
}
