package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.service.command.CancelCommandHandler;
import backend.academy.linktracker.bot.service.command.HelpCommandHandler;
import backend.academy.linktracker.bot.service.command.ListCommandHandler;
import backend.academy.linktracker.bot.service.command.StartCommandHandler;
import backend.academy.linktracker.bot.service.command.TrackCommandHandler;
import backend.academy.linktracker.bot.service.command.UntrackCommandHandler;
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
class HelpCommandIntegrationTest {

    @Test
    void helpCommandReturnsSupportedCommandsList() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("help-command")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": 123457,
                                      "message": {
                                        "message_id": 2,
                                        "from": {
                                          "id": 123456789,
                                          "is_bot": false,
                                          "first_name": "Test"
                                        },
                                        "chat": {
                                          "id": 987654321,
                                          "type": "private"
                                        },
                                        "date": 1234567891,
                                        "text": "/help"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("consumed"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("help-command")
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
                                    "message_id": 3
                                  }
                                }
                                """)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(1, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
            var body = findAll(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")))
                    .getFirst()
                    .getBodyAsString();
            assertEquals("987654321", TelegramRequestBodyParser.getFormFieldValue(body, "chat_id"));
            assertEquals(expectedHelpMessage(), TelegramRequestBodyParser.getFormFieldValue(body, "text"));
        });
    }

    private String expectedHelpMessage() {
        return "Доступные команды:"
                + System.lineSeparator()
                + CancelCommandHandler.COMMAND
                + " - "
                + CancelCommandHandler.DESCRIPTION
                + System.lineSeparator()
                + HelpCommandHandler.COMMAND
                + " - "
                + HelpCommandHandler.DESCRIPTION
                + System.lineSeparator()
                + ListCommandHandler.COMMAND
                + " - "
                + ListCommandHandler.DESCRIPTION
                + System.lineSeparator()
                + StartCommandHandler.COMMAND
                + " - "
                + StartCommandHandler.DESCRIPTION
                + System.lineSeparator()
                + TrackCommandHandler.COMMAND
                + " - "
                + TrackCommandHandler.DESCRIPTION
                + System.lineSeparator()
                + UntrackCommandHandler.COMMAND
                + " - "
                + UntrackCommandHandler.DESCRIPTION;
    }
}
