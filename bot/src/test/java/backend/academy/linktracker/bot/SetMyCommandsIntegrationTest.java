package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;

import backend.academy.linktracker.bot.service.TelegramCommandMenuRegistrar;
import backend.academy.linktracker.bot.service.command.CommandRegistry;
import com.pengrad.telegrambot.TelegramBot;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock
@TestPropertySource(properties = {"app.telegram.polling-enabled=false", "app.telegram.set-my-commands-enabled=false"})
class SetMyCommandsIntegrationTest {

    @Autowired
    private TelegramBot telegramBot;

    @Autowired
    private CommandRegistry commandRegistry;

    @Test
    void botRegistersCommandsInTelegramMenuOnStartup() {
        stubFor(post(urlMatching("/bot[^/]+/setMyCommands"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": true
                                }
                                """)));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));

        var telegramCommandMenuRegistrar = new TelegramCommandMenuRegistrar(telegramBot, commandRegistry);
        ReflectionTestUtils.invokeMethod(telegramCommandMenuRegistrar, "registerCommands");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(postRequestedFor(urlMatching("/bot[^/]+/setMyCommands"))
                        .withRequestBody(containing("%2Fcancel"))
                        .withRequestBody(containing("%2Fstart"))
                        .withRequestBody(containing("%2Fhelp"))
                        .withRequestBody(containing("%2Ftrack"))
                        .withRequestBody(containing("%2Flist"))
                        .withRequestBody(containing("%2Funtrack"))));
    }
}
