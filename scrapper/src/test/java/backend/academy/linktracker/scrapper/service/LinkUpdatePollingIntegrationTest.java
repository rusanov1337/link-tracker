package backend.academy.linktracker.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Test
    void checkUpdatesDoesNotDuplicateNotificationWhenCalledConcurrently() throws Exception {
        stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(200).withBody("""
                                {
                                  "updated_at": "2099-01-01T00:00:00Z"
                                }
                                """)));
        stubFor(post(urlEqualTo("/updates")).willReturn(aResponse().withStatus(200)));

        scrapperLinkService.registerChat(1L);
        scrapperLinkService.addLink(
                1L, new AddLinkRequest("https://github.com/octocat/hello-world", List.of(), List.of()));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var startLatch = new CountDownLatch(1);
            var first = executor.submit(() -> {
                awaitStart(startLatch);
                linkUpdatePollingService.checkUpdates();
            });
            var second = executor.submit(() -> {
                awaitStart(startLatch);
                linkUpdatePollingService.checkUpdates();
            });

            startLatch.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(1, postRequestedFor(urlEqualTo("/updates")));
    }

    private void awaitStart(CountDownLatch startLatch) {
        try {
            startLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent start", exception);
        }
    }
}
