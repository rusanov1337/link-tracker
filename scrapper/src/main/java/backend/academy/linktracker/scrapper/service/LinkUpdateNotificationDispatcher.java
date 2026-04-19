package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdateNotificationDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdateNotificationDispatcher.class);

    private final BotUpdatesClient botUpdatesClient;
    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter;
    private final PendingProcessingFailureReportStore pendingFailureReportStore;

    public LinkUpdateNotificationDispatcher(
            BotUpdatesClient botUpdatesClient,
            LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter,
            PendingProcessingFailureReportStore pendingFailureReportStore) {
        this.botUpdatesClient = botUpdatesClient;
        this.linkUpdateDescriptionFormatter = linkUpdateDescriptionFormatter;
        this.pendingFailureReportStore = pendingFailureReportStore;
    }

    void sendNotifications(List<PendingNotification> notifications, Map<Long, Set<URI>> failedLinksByChat) {
        for (var notification : notifications) {
            if (!notifyBot(
                    notification.trackedLinkId(),
                    notification.trackedLinkUrl(),
                    notification.update(),
                    notification.chatIds())) {
                recordFailedLink(notification.trackedLinkUrl(), notification.chatIds(), failedLinksByChat);
            }
        }
    }

    void sendFailureReports(Map<Long, Set<URI>> failedLinksByChat) {
        pendingFailureReportStore.merge(failedLinksByChat);
        for (var entry : pendingFailureReportStore.snapshot().entrySet()) {
            var description = buildFailureReport(entry.getValue());
            try {
                botUpdatesClient.sendProcessingFailureReport(description, List.of(entry.getKey()));
                pendingFailureReportStore.remove(entry.getKey(), entry.getValue());
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

    private boolean notifyBot(long trackedLinkId, URI trackedLinkUrl, DetectedUpdate update, List<Long> chatIds) {
        var description = linkUpdateDescriptionFormatter.format(update);
        try {
            botUpdatesClient.sendLinkUpdate(trackedLinkId, trackedLinkUrl, description, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }

    private void recordFailedLink(URI trackedLinkUrl, List<Long> chatIds, Map<Long, Set<URI>> failedLinksByChat) {
        for (var chatId : chatIds) {
            failedLinksByChat
                    .computeIfAbsent(chatId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(trackedLinkUrl);
        }
    }

    private String buildFailureReport(Collection<URI> failedUrls) {
        var uniqueUrls = failedUrls.stream().map(URI::toString).sorted().toList();
        return "Не удалось обработать ссылки:" + System.lineSeparator()
                + uniqueUrls.stream().map(url -> "- " + url).collect(Collectors.joining(System.lineSeparator()));
    }
}
