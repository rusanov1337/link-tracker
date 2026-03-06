package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
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
class UntrackCommandIntegrationTest {

    @Test
    void untrackCommandRemovesLink() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("untrack-command")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": 223112,
                                      "message": {
                                        "message_id": 31,
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
                                        "text": "/untrack https://github.com/user/repo"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("untrack-command")
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

        stubFor(post(urlEqualTo("/tg-chat/987654321")).willReturn(aResponse().withStatus(200)));

        stubFor(WireMock.delete(urlEqualTo("/links"))
                .withHeader("Tg-Chat-Id", containing("987654321"))
                .withRequestBody(containing("\"link\":\"https://github.com/user/repo\""))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "id": 1,
                                  "url": "https://github.com/user/repo",
                                  "tags": ["work"],
                                  "filters": []
                                }
                                """)));

        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 32
                                  }
                                }
                                """)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(1, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            var body = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")))
                    .getFirst()
                    .getBodyAsString();
            assertEquals("987654321", TelegramRequestBodyParser.getFormFieldValue(body, "chat_id"));
            assertEquals("Ссылка удалена из отслеживания.", TelegramRequestBodyParser.getFormFieldValue(body, "text"));
        });
    }
}
