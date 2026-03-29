package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
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

    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final List<ExternalLinkClient> externalLinkClients;
    private final BotUpdatesClient botUpdatesClient;
    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter;
    private final SchedulerProperties schedulerProperties;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            BotUpdatesClient botUpdatesClient,
            LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter,
            SchedulerProperties schedulerProperties) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.botUpdatesClient = botUpdatesClient;
        this.linkUpdateDescriptionFormatter = linkUpdateDescriptionFormatter;
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

            var checkResult = client.orElseThrow().fetchUpdates(trackedLink);
            processFetchedState(trackedLink, checkedAt, checkResult);
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

    private void processFetchedState(TrackedLink trackedLink, Instant checkedAt, LinkCheckResult checkResult) {
        var currentState = trackedLink.withLastCheckedAt(checkedAt);
        if (checkResult.updates().isEmpty()) {
            trackedLinkRepository.update(currentState);
            return;
        }

        var chatIds = linkSubscriptionRepository.findByLinkId(trackedLink.id()).stream()
                .map(subscription -> subscription.chatId())
                .toList();
        for (var update : checkResult.updates()) {
            if (!chatIds.isEmpty() && !notifyBot(trackedLink, update, chatIds)) {
                trackedLinkRepository.update(currentState);
                return;
            }

            currentState = currentState.withLastUpdatedAt(update.createdAt())
                    .withLastEventState(update.createdAt(), update.cursor());
        }

        trackedLinkRepository.update(currentState);
    }

    private boolean notifyBot(TrackedLink trackedLink, DetectedUpdate update, List<Long> chatIds) {
        var description = linkUpdateDescriptionFormatter.format(update);
        try {
            botUpdatesClient.sendLinkUpdate(trackedLink.id(), trackedLink.url(), description, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }
}
