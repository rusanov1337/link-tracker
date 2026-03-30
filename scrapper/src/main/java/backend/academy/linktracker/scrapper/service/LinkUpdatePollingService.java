package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        var failedLinksByChat = new LinkedHashMap<Long, List<URI>>();
        long afterId = 0;
        while (true) {
            var links = trackedLinkRepository.findPageToCheck(checkedAt, afterId, schedulerProperties.getBatchSize());
            if (links.isEmpty()) {
                break;
            }

            for (var trackedLink : links) {
                checkSingleLink(trackedLink, checkedAt, failedLinksByChat);
            }

            checkedLinksCount += links.size();
            afterId = links.getLast().id();
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "checkUpdates")
                .addKeyValue("linksChecked", checkedLinksCount)
                .log("Links check finished");
        sendFailureReports(failedLinksByChat);
    }

    private void checkSingleLink(TrackedLink trackedLink, Instant checkedAt, Map<Long, List<URI>> failedLinksByChat) {
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
            if (!processFetchedState(trackedLink, checkedAt, checkResult)) {
                recordFailedLink(trackedLink, failedLinksByChat);
            }
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "checkSingleLink")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("Link check failed");
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            recordFailedLink(trackedLink, failedLinksByChat);
        }
    }

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }

    private boolean processFetchedState(TrackedLink trackedLink, Instant checkedAt, LinkCheckResult checkResult) {
        var currentState = trackedLink.withLastCheckedAt(checkedAt);
        if (checkResult.failed()) {
            trackedLinkRepository.update(currentState);
            return false;
        }
        if (checkResult.updates().isEmpty()) {
            trackedLinkRepository.update(currentState);
            return true;
        }

        var chatIds = linkSubscriptionRepository.findByLinkId(trackedLink.id()).stream()
                .map(subscription -> subscription.chatId())
                .toList();
        for (var update : checkResult.updates()) {
            if (!chatIds.isEmpty() && !notifyBot(trackedLink, update, chatIds)) {
                trackedLinkRepository.update(currentState);
                return false;
            }

            currentState = currentState.withLastUpdatedAt(update.createdAt())
                    .withLastEventState(update.createdAt(), update.cursor());
        }

        trackedLinkRepository.update(currentState);
        return true;
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

    private void recordFailedLink(TrackedLink trackedLink, Map<Long, List<URI>> failedLinksByChat) {
        for (var subscription : linkSubscriptionRepository.findByLinkId(trackedLink.id())) {
            failedLinksByChat
                    .computeIfAbsent(subscription.chatId(), ignored -> new ArrayList<>())
                    .add(trackedLink.url());
        }
    }

    private void sendFailureReports(Map<Long, List<URI>> failedLinksByChat) {
        for (var entry : failedLinksByChat.entrySet()) {
            var description = buildFailureReport(entry.getValue());
            try {
                botUpdatesClient.sendProcessingFailureReport(description, List.of(entry.getKey()));
            } catch (RuntimeException exception) {
                LOGGER.atWarn()
                        .addKeyValue("operation", "sendFailureReport")
                        .addKeyValue("chatId", entry.getKey())
                        .addKeyValue("failedLinksCount", entry.getValue().size())
                        .setCause(exception)
                        .log("Failed links report delivery failed");
            }
        }
    }

    private String buildFailureReport(List<URI> failedUrls) {
        var uniqueUrls = failedUrls.stream().map(URI::toString).distinct().sorted().toList();
        return "Не удалось обработать ссылки:" + System.lineSeparator() + uniqueUrls.stream()
                .map(url -> "- " + url)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }
}
