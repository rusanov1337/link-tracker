package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdatePollingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdatePollingService.class);
    private static final String UPDATE_MESSAGE = "Обнаружено обновление отслеживаемой ссылки";

    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final List<ExternalLinkClient> externalLinkClients;
    private final BotUpdatesClient botUpdatesClient;
    private final SchedulerProperties schedulerProperties;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            BotUpdatesClient botUpdatesClient,
            SchedulerProperties schedulerProperties) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.botUpdatesClient = botUpdatesClient;
        this.schedulerProperties = schedulerProperties;
    }

    public void checkUpdates() {
        var checkedAt = Instant.now();
        var checkedLinksCount = 0;
        long afterId = 0;
        while (true) {
            var links = trackedLinkRepository.findPageToCheck(checkedAt, afterId, schedulerProperties.getBatchSize());
            if (links.isEmpty()) {
                break;
            }

            for (var trackedLink : links) {
                checkSingleLink(trackedLink, checkedAt);
            }

            checkedLinksCount += links.size();
            afterId = links.getLast().id();
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "checkUpdates")
                .addKeyValue("linksChecked", checkedLinksCount)
                .log("Links check finished");
    }

    private void checkSingleLink(TrackedLink trackedLink, Instant checkedAt) {
        try {
            var client = findClient(trackedLink);
            if (client.isEmpty()) {
                trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
                LOGGER.atDebug()
                        .addKeyValue("linkId", trackedLink.id())
                        .addKeyValue("url", trackedLink.url())
                        .log("No external client supports URL");
                return;
            }

            var lastUpdated = client.orElseThrow().fetchLastUpdated(trackedLink.url());
            processFetchedState(trackedLink, checkedAt, lastUpdated);
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "checkSingleLink")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("Link check failed");
        }
    }

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }

    private void processFetchedState(TrackedLink trackedLink, Instant checkedAt, Optional<Instant> lastUpdated) {
        if (lastUpdated.isEmpty() || !lastUpdated.orElseThrow().isAfter(trackedLink.lastUpdatedAt())) {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            return;
        }

        var chatIds = linkSubscriptionRepository.findByLinkId(trackedLink.id()).stream()
                .map(subscription -> subscription.chatId())
                .toList();
        if (chatIds.isEmpty()) {
            trackedLinkRepository.update(
                    trackedLink.withLastCheckedAt(checkedAt).withLastUpdatedAt(lastUpdated.orElseThrow()));
            return;
        }

        if (notifyBot(trackedLink, chatIds)) {
            trackedLinkRepository.update(
                    trackedLink.withLastCheckedAt(checkedAt).withLastUpdatedAt(lastUpdated.orElseThrow()));
        } else {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
        }
    }

    private boolean notifyBot(TrackedLink trackedLink, List<Long> chatIds) {
        try {
            botUpdatesClient.sendLinkUpdate(trackedLink.id(), trackedLink.url(), UPDATE_MESSAGE, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }
}
