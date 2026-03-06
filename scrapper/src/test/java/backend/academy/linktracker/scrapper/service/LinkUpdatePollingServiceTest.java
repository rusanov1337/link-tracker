package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClientException;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.memory.InMemoryLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.memory.InMemoryTrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkUpdatePollingServiceTest {

    @Test
    void checkUpdatesSendsNotificationWhenLinkWasUpdated() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-01-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of("work"), List.of()));

        var externalClient =
                new StubExternalLinkClient(trackedLink.url(), Optional.of(Instant.parse("2025-01-02T00:00:00Z")));
        var botClient = new RecordingBotUpdatesClient(false);
        var service = new LinkUpdatePollingService(
                trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient);

        service.checkUpdates();

        assertEquals(1, botClient.notifications.size());
        assertEquals(List.of(11L), botClient.notifications.getFirst().tgChatIds());
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(Instant.parse("2025-01-02T00:00:00Z"), storedLink.lastUpdatedAt());
        assertTrue(storedLink.lastCheckedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void checkUpdatesDoesNotNotifyWhenNothingChanged() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-02-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of(), List.of()));

        var externalClient = new StubExternalLinkClient(trackedLink.url(), Optional.of(initialUpdatedAt));
        var botClient = new RecordingBotUpdatesClient(false);
        var service = new LinkUpdatePollingService(
                trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient);

        service.checkUpdates();

        assertEquals(0, botClient.notifications.size());
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(initialUpdatedAt, storedLink.lastUpdatedAt());
        assertTrue(storedLink.lastCheckedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void checkUpdatesKeepsLastUpdatedWhenBotNotificationFailed() {
        var trackedLinkRepository = new InMemoryTrackedLinkRepository();
        var linkSubscriptionRepository = new InMemoryLinkSubscriptionRepository();
        var initialUpdatedAt = Instant.parse("2025-03-01T00:00:00Z");
        var trackedLink = trackedLinkRepository.create(URI.create("https://github.com/user/repo"), initialUpdatedAt);
        linkSubscriptionRepository.add(new LinkSubscription(11L, trackedLink.id(), List.of(), List.of()));

        var externalClient =
                new StubExternalLinkClient(trackedLink.url(), Optional.of(Instant.parse("2025-03-05T00:00:00Z")));
        var botClient = new RecordingBotUpdatesClient(true);
        var service = new LinkUpdatePollingService(
                trackedLinkRepository, linkSubscriptionRepository, List.of(externalClient), botClient);

        service.checkUpdates();

        assertEquals(1, botClient.attempts);
        var storedLink = trackedLinkRepository.findById(trackedLink.id()).orElseThrow();
        assertEquals(initialUpdatedAt, storedLink.lastUpdatedAt());
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

        var changedClient =
                new StubExternalLinkClient(changedLink.url(), Optional.of(Instant.parse("2025-04-02T00:00:00Z")));
        var unchangedClient = new StubExternalLinkClient(unchangedLink.url(), Optional.of(initialUpdatedAt));
        var botClient = new RecordingBotUpdatesClient(false);
        var service = new LinkUpdatePollingService(
                trackedLinkRepository, linkSubscriptionRepository, List.of(changedClient, unchangedClient), botClient);

        service.checkUpdates();

        assertEquals(1, botClient.notifications.size());
        assertEquals(List.of(11L, 22L), botClient.notifications.getFirst().tgChatIds());
    }

    private record Notification(long id, URI url, String description, List<Long> tgChatIds) {}

    private static final class RecordingBotUpdatesClient implements BotUpdatesClient {
        private final List<Notification> notifications = new ArrayList<>();
        private final boolean fail;
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
    }

    private static final class StubExternalLinkClient implements ExternalLinkClient {
        private final URI supportedUrl;
        private final Optional<Instant> fetchedLastUpdated;

        private StubExternalLinkClient(URI supportedUrl, Optional<Instant> fetchedLastUpdated) {
            this.supportedUrl = supportedUrl;
            this.fetchedLastUpdated = fetchedLastUpdated;
        }

        @Override
        public boolean supports(URI url) {
            return supportedUrl.equals(url);
        }

        @Override
        public Optional<Instant> fetchLastUpdated(URI url) {
            return fetchedLastUpdated;
        }
    }
}
