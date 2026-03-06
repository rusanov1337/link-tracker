package backend.academy.linktracker.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(properties = "app.scheduler.enabled=false")
@ActiveProfiles("test")
@EnableWireMock
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LinkUpdatePollingIntegrationTest {

    @Autowired
    private LinkUpdatePollingService linkUpdatePollingService;

    @Autowired
    private ScrapperLinkService scrapperLinkService;

    @Test
    void checkUpdatesSendsNotificationToTrackedChats() {
        stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                {
                                  "updated_at": "2099-01-01T00:00:00Z"
                                }
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.registerChat(2L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));
        scrapperLinkService.addLink(
                2L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(
                1,
                postRequestedFor(urlEqualTo("/updates"))
                        .withRequestBody(containing("\"url\":\"https://github.com/octocat/hello-world\""))
                        .withRequestBody(containing("\"tgChatIds\":[1,2]")));
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiFails() {
        stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .willReturn(aResponse().withStatus(503)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiBodyIsMalformed() {
        stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .willReturn(aResponse().withStatus(200).withBody("{\"updated_at\":")));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
    }
}
