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
class UnknownCommandIntegrationTest {

    @Test
    void unknownCommandReturnsErrorMessage() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("unknown-command")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": 123458,
                                      "message": {
                                        "message_id": 3,
                                        "from": {
                                          "id": 123456789,
                                          "is_bot": false,
                                          "first_name": "Test"
                                        },
                                        "chat": {
                                          "id": 987654321,
                                          "type": "private"
                                        },
                                        "date": 1234567892,
                                        "text": "/abracadabra"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("unknown-command")
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

        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 4
                                  }
                                }
                                """)));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(
                        1,
                        postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                                .withRequestBody(containing("chat_id=987654321"))
                                .withRequestBody(
                                        containing(
                                                "%D0%9D%D0%B5%D0%B8%D0%B7%D0%B2%D0%B5%D1%81%D1%82%D0%BD%D0%B0%D1%8F%20%D0%BA%D0%BE%D0%BC%D0%B0%D0%BD%D0%B4%D0%B0"))
                                .withRequestBody(containing("%2Fhelp"))));
    }
}
