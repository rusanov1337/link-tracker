package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock
@TestPropertySource(properties = "app.telegram.polling-enabled=true")
class ListCommandIntegrationTest {

    @Test
    void listCommandReturnsTrackedLinks() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("list-command")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": 223111,
                                      "message": {
                                        "message_id": 21,
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
                                        "text": "/list"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("list-command")
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

        stubFor(get(urlEqualTo("/links"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "links": [
                                    {
                                      "id": 1,
                                      "url": "https://github.com/user/repo",
                                      "tags": ["work"],
                                      "filters": []
                                    },
                                    {
                                      "id": 2,
                                      "url": "https://stackoverflow.com/questions/42",
                                      "tags": ["study"],
                                      "filters": []
                                    }
                                  ],
                                  "size": 2
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
                                    "message_id": 22
                                  }
                                }
                                """)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(1, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            verify(0, postRequestedFor(urlEqualTo("/tg-chat/987654321")));
            var body = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")))
                    .getFirst()
                    .getBodyAsString();
            assertEquals("987654321", TelegramRequestBodyParser.getFormFieldValue(body, "chat_id"));
            assertEquals(
                    "Отслеживаемые ссылки:"
                            + System.lineSeparator()
                            + "1. https://github.com/user/repo"
                            + System.lineSeparator()
                            + "2. https://stackoverflow.com/questions/42",
                    TelegramRequestBodyParser.getFormFieldValue(body, "text"));
        });
    }

    @Test
    void listCommandWithTagReturnsOnlyMatchingLinks() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("list-command-tag")
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
                                        "message_id": 22,
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
                                        "text": "/list work"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("list-command-tag")
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

        stubFor(get(urlEqualTo("/links"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "links": [
                                    {
                                      "id": 1,
                                      "url": "https://github.com/user/repo",
                                      "tags": ["work"],
                                      "filters": []
                                    },
                                    {
                                      "id": 2,
                                      "url": "https://stackoverflow.com/questions/42",
                                      "tags": ["study"],
                                      "filters": []
                                    }
                                  ],
                                  "size": 2
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
                                    "message_id": 23
                                  }
                                }
                                """)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(1, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            verify(0, postRequestedFor(urlEqualTo("/tg-chat/987654321")));
            var body = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")))
                    .getFirst()
                    .getBodyAsString();
            assertEquals("987654321", TelegramRequestBodyParser.getFormFieldValue(body, "chat_id"));
            assertEquals(
                    "Отслеживаемые ссылки с тегом 'work':" + System.lineSeparator() + "1. https://github.com/user/repo",
                    TelegramRequestBodyParser.getFormFieldValue(body, "text"));
        });
    }
}
