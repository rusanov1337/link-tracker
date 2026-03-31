package backend.academy.linktracker.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        properties = {
            "app.scheduler.enabled=false",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(backend.academy.linktracker.scrapper.TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LinkUpdatePollingIntegrationTest extends DatabaseCleanupSupport {

    @Autowired
    private LinkUpdatePollingService linkUpdatePollingService;

    @Autowired
    private ScrapperLinkService scrapperLinkService;

    @Test
    void checkUpdatesSendsNotificationToTrackedChats() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .withQueryParam("state", containing("all"))
                .withQueryParam("sort", containing("created"))
                .withQueryParam("direction", containing("desc"))
                .withQueryParam("per_page", containing("100"))
                .willReturn(aResponse().withStatus(200).withBody("""
                                [
                                  {
                                    "id": 101,
                                    "title": "New issue",
                                    "created_at": "2099-01-01T00:00:00Z",
                                    "body_text": "Issue preview",
                                    "user": { "login": "octocat" }
                                  }
                                ]
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
                        .withRequestBody(containing("\"tgChatIds\":[1,2]"))
                        .withRequestBody(containing("New issue"))
                        .withRequestBody(containing("octocat"))
                        .withRequestBody(containing("Issue preview")));
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiFails() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
        verify(
                1,
                postRequestedFor(urlEqualTo("/reports"))
                        .withRequestBody(containing("https://github.com/octocat/hello-world")));
    }

    @Test
    void checkUpdatesSkipsBotNotificationWhenExternalApiBodyIsMalformed() {
        stubFor(get(urlPathEqualTo("/repos/octocat/hello-world/issues"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"id\":")));
        stubFor(post(urlEqualTo("/reports")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        linkUpdatePollingService.checkUpdates();

        verify(0, postRequestedFor(urlEqualTo("/updates")));
        verify(
                1,
                postRequestedFor(urlEqualTo("/reports"))
                        .withRequestBody(containing("https://github.com/octocat/hello-world")));
    }
}
