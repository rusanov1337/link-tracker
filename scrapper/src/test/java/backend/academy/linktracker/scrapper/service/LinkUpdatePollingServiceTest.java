package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClientException;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.memory.InMemoryLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.memory.InMemoryTrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkUpdatePollingServiceTest {

    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter = new LinkUpdateDescriptionFormatter();

    @Test
    void checkUpdatesSendsNotificationWhenLinkWasUpdated() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-01-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of("work"), List.of()));

        var externalClient = new StubExternalLinkClient(
                trackedLink.url(),
                new LinkCheckResult(
                        Optional.of("101"),
                        List.of(new DetectedUpdate(
                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                "Issue title",
                                "octocat",
                                Instant.parse("2025-01-02T00:00:00Z"),
                                "Issue preview",
                                "101"))));
        var botClient = new RecordingBotUpdatesClient(false);
        var service =
                newService(trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient, 100);

        service.checkUpdates();

        assertEquals(1, botClient.notifications.size());
        assertEquals(List.of(11L), botClient.notifications.getFirst().tgChatIds());
        assertTrue(botClient.notifications.getFirst().description().contains("Issue title"));
        assertTrue(botClient.notifications.getFirst().description().contains("octocat"));
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(Instant.parse("2025-01-02T00:00:00Z"), storedLink.lastUpdatedAt());
        assertEquals(Instant.parse("2025-01-02T00:00:00Z"), storedLink.lastEventAt());
        assertEquals("101", storedLink.lastEventCursor());
        assertTrue(storedLink.lastCheckedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void checkUpdatesDoesNotNotifyWhenNothingChanged() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-02-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of(), List.of()));

        var externalClient = new StubExternalLinkClient(trackedLink.url(), LinkCheckResult.empty());
        var botClient = new RecordingBotUpdatesClient(false);
        var service =
                newService(trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient, 100);

        service.checkUpdates();

        assertEquals(0, botClient.notifications.size());
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(initialUpdatedAt, storedLink.lastUpdatedAt());
        assertEquals(null, storedLink.lastEventAt());
        assertEquals(null, storedLink.lastEventCursor());
        assertTrue(storedLink.lastCheckedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void checkUpdatesSendsFailureReportWhenExternalCheckFailed() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var trackedLink = trackedLinkRepository.create(
                URI.create("https://github.com/user/repo"), Instant.parse("2025-06-01T00:00:00Z"));
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of(), List.of()));
        linkSubscriptionRepository.add(new LinkSubscription(22L, trackedLink.id(), List.of(), List.of()));

        var externalClient = new StubExternalLinkClient(trackedLink.url(), LinkCheckResult.failure());
        var botClient = new RecordingBotUpdatesClient(false);
        var service =
                newService(trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient, 100);

        service.checkUpdates();

        assertEquals(0, botClient.notifications.size());
        assertEquals(2, botClient.reports.size());
        assertTrue(botClient.reports.getFirst().description().contains("Не удалось обработать ссылки"));
        assertTrue(botClient.reports.getFirst().description().contains(trackedLink.url().toString()));
    }

    @Test
    void checkUpdatesKeepsLastUpdatedWhenBotNotificationFailed() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-03-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of(), List.of()));

        var externalClient = new StubExternalLinkClient(
                trackedLink.url(),
                new LinkCheckResult(
                        Optional.of("102"),
                        List.of(new DetectedUpdate(
                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                "Issue title",
                                "octocat",
                                Instant.parse("2025-03-05T00:00:00Z"),
                                "Issue preview",
                                "102"))));
        var botClient = new RecordingBotUpdatesClient(true);
        var service =
                newService(trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient, 100);

        service.checkUpdates();

        assertEquals(1, botClient.attempts);
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(initialUpdatedAt, storedLink.lastUpdatedAt());
        assertEquals(null, storedLink.lastEventAt());
        assertEquals(null, storedLink.lastEventCursor());
        assertTrue(storedLink.lastCheckedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void checkUpdatesNotifiesOnlySubscribersOfChangedLink() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-04-01T00:00:00Z");
        var changedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        var unchangedLink = trackedLinkRepository.create(URI.create("https://github.com/other/repo"), initialUpdatedAt);

        linkSubscriptionRepository.add(new LinkSubscription(11L, changedLink.id(), List.of("work"), List.of()));
        linkSubscriptionRepository.add(new LinkSubscription(22L, changedLink.id(), List.of("docs"), List.of()));
        linkSubscriptionRepository.add(new LinkSubscription(33L, unchangedLink.id(), List.of("misc"), List.of()));

        var changedClient = new StubExternalLinkClient(
                changedLink.url(),
                new LinkCheckResult(
                        Optional.of("103"),
                        List.of(new DetectedUpdate(
                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                "Issue title",
                                "octocat",
                                Instant.parse("2025-04-02T00:00:00Z"),
                                "Issue preview",
                                "103"))));
        var unchangedClient = new StubExternalLinkClient(unchangedLink.url(), LinkCheckResult.empty());
        var botClient = new RecordingBotUpdatesClient(false);
        var service = newService(
                trackedLinkRepository,
                linkSubscriptionRepository,
                List.of(changedClient, unchangedClient),
                botClient,
                100);

        service.checkUpdates();

        assertEquals(1, botClient.notifications.size());
        assertEquals(List.of(11L, 22L), botClient.notifications.getFirst().tgChatIds());
    }

    @Test
    void checkUpdatesProcessesAllLinksAcrossBatches() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-05-01T00:00:00Z");
        var first = trackedLinkRepository.create(URI.create("https://github.com/user/one"), initialUpdatedAt);
        var second = trackedLinkRepository.create(URI.create("https://github.com/user/two"), initialUpdatedAt);
        var third = trackedLinkRepository.create(URI.create("https://github.com/user/three"), initialUpdatedAt);

        linkSubscriptionRepository.add(new LinkSubscription(11L, first.id(), List.of(), List.of()));
        linkSubscriptionRepository.add(new LinkSubscription(22L, second.id(), List.of(), List.of()));
        linkSubscriptionRepository.add(new LinkSubscription(33L, third.id(), List.of(), List.of()));

        var botClient = new RecordingBotUpdatesClient(false);
        var service = newService(
                trackedLinkRepository,
                linkSubscriptionRepository,
                List.of(
                        new StubExternalLinkClient(
                                first.url(),
                                new LinkCheckResult(
                                        Optional.of("201"),
                                        List.of(new DetectedUpdate(
                                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                                "First issue",
                                                "octocat",
                                                initialUpdatedAt.plusSeconds(60),
                                                "Issue preview",
                                                "201")))),
                        new StubExternalLinkClient(
                                second.url(),
                                new LinkCheckResult(
                                        Optional.of("202"),
                                        List.of(new DetectedUpdate(
                                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                                "Second issue",
                                                "octocat",
                                                initialUpdatedAt.plusSeconds(60),
                                                "Issue preview",
                                                "202")))),
                        new StubExternalLinkClient(
                                third.url(),
                                new LinkCheckResult(
                                        Optional.of("203"),
                                        List.of(new DetectedUpdate(
                                                backend.academy.linktracker.scrapper.domain.UpdateProvider.GITHUB,
                                                backend.academy.linktracker.scrapper.domain.UpdateEventType.ISSUE,
                                                "Third issue",
                                                "octocat",
                                                initialUpdatedAt.plusSeconds(60),
                                                "Issue preview",
                                                "203"))))),
                botClient,
                2);

        service.checkUpdates();

        assertEquals(3, botClient.notifications.size());
    }

    private LinkUpdatePollingService newService(
            InMemoryTrackedLinkRepository trackedLinkRepository,
            InMemoryLinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalClients,
            RecordingBotUpdatesClient botClient,
            int batchSize) {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setBatchSize(batchSize);
        return new LinkUpdatePollingService(
                trackedLinkRepository,
                linkSubscriptionRepository,
                externalClients,
                botClient,
                linkUpdateDescriptionFormatter,
                schedulerProperties);
    }

    private record Notification(long id, URI url, String description, List<Long> tgChatIds) {}

    private static final class RecordingBotUpdatesClient implements BotUpdatesClient {
        private final List<Notification> notifications = new ArrayList<>();
        private final boolean fail;
        private final List<ReportNotification> reports = new ArrayList<>();
        private int attempts;

        private RecordingBotUpdatesClient(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
            attempts++;
            if (fail) {
                throw new BotUpdatesClientException("Notification failed", new IllegalStateException("failure"));
            }
            notifications.add(new Notification(id, url, description, List.copyOf(tgChatIds)));
        }

        @Override
        public void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
            for (var chatId : tgChatIds) {
                reports.add(new ReportNotification(chatId, description));
            }
        }
    }

    private record ReportNotification(long chatId, String description) {}

    private static final class StubExternalLinkClient implements ExternalLinkClient {
        private final URI supportedUrl;
        private final LinkCheckResult checkResult;

        private StubExternalLinkClient(URI supportedUrl, LinkCheckResult checkResult) {
            this.supportedUrl = supportedUrl;
            this.checkResult = checkResult;
        }

        @Override
        public boolean supports(URI url) {
            return supportedUrl.equals(url);
        }

        @Override
        public LinkCheckResult fetchUpdates(
                backend.academy.linktracker.scrapper.domain.TrackedLink trackedLink) {
            return checkResult;
        }
    }
}
