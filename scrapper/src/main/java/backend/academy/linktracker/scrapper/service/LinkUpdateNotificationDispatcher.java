package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.repository.NotificationOutboxRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkUpdateNotificationDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdateNotificationDispatcher.class);

    private final BotUpdatesClient botUpdatesClient;
    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final ScrapperMetricsService scrapperMetricsService;

    public LinkUpdateNotificationDispatcher(
            BotUpdatesClient botUpdatesClient,
            LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter,
            NotificationOutboxRepository notificationOutboxRepository,
            PlatformTransactionManager transactionManager,
            ScrapperMetricsService scrapperMetricsService) {
        this.botUpdatesClient = botUpdatesClient;
        this.linkUpdateDescriptionFormatter = linkUpdateDescriptionFormatter;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.scrapperMetricsService = scrapperMetricsService;
    }

    void stageNotifications(List<PendingNotification> notifications, Instant createdAt) {
        if (notifications.isEmpty()) {
            return;
        }

        notificationOutboxRepository.saveLinkUpdates(toOutboxNotifications(notifications), createdAt);
    }

    void publishPendingNotifications(Map<Long, Set<URI>> failedLinksByChat, int batchSize, Duration processingLease) {
        var processingOwner = UUID.randomUUID().toString();
        var claimedAt = Instant.now();
        var processingUntil = claimedAt.plus(processingLease);
        var notifications =
                transactionTemplate.execute(status -> notificationOutboxRepository.claimNextLinkUpdatesToPublish(
                        processingOwner, claimedAt, processingUntil, batchSize));
        if (notifications.isEmpty()) {
            return;
        }

        for (var notification : notifications) {
            if (notifyBot(
                    notification.trackedLinkId(),
                    notification.trackedLinkUrl(),
                    notification.description(),
                    notification.chatIds())) {
                notificationOutboxRepository.markProcessed(notification.id(), processingOwner, Instant.now());
            } else {
                notificationOutboxRepository.release(notification.id(), processingOwner);
                recordFailedLink(notification.trackedLinkUrl(), notification.chatIds(), failedLinksByChat);
            }
        }
    }

    void stageFailureReports(Map<Long, Set<URI>> failedLinksByChat, Instant createdAt) {
        if (failedLinksByChat.isEmpty()) {
            return;
        }

        var reports = new ArrayList<OutboxFailureReport>(failedLinksByChat.size());
        for (var entry : failedLinksByChat.entrySet()) {
            reports.add(new OutboxFailureReport(0L, buildFailureReport(entry.getValue()), List.of(entry.getKey())));
        }
        notificationOutboxRepository.saveProcessingFailureReports(List.copyOf(reports), createdAt);
    }

    void publishPendingFailureReports(int batchSize, Duration processingLease) {
        var processingOwner = UUID.randomUUID().toString();
        var claimedAt = Instant.now();
        var processingUntil = claimedAt.plus(processingLease);
        var reports = transactionTemplate.execute(
                status -> notificationOutboxRepository.claimNextProcessingFailureReportsToPublish(
                        processingOwner, claimedAt, processingUntil, batchSize));
        if (reports.isEmpty()) {
            return;
        }

        for (var report : reports) {
            if (sendFailureReport(report)) {
                notificationOutboxRepository.markProcessed(report.id(), processingOwner, Instant.now());
            } else {
                notificationOutboxRepository.release(report.id(), processingOwner);
            }
        }
    }

    private List<OutboxNotification> toOutboxNotifications(List<PendingNotification> notifications) {
        var outboxNotifications = new ArrayList<OutboxNotification>(notifications.size());
        for (var notification : notifications) {
            outboxNotifications.add(new OutboxNotification(
                    0L,
                    notification.trackedLinkId(),
                    notification.trackedLinkUrl(),
                    linkUpdateDescriptionFormatter.format(notification.update()),
                    notification.chatIds()));
        }
        return List.copyOf(outboxNotifications);
    }

    private boolean notifyBot(long trackedLinkId, URI trackedLinkUrl, String description, List<Long> chatIds) {
        try {
            var startNanos = System.nanoTime();
            botUpdatesClient.sendLinkUpdate(trackedLinkId, trackedLinkUrl, description, chatIds);
            scrapperMetricsService.recordRequestDuration(
                    "bot_transport", "link_update", System.nanoTime() - startNanos);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }

    private boolean sendFailureReport(OutboxFailureReport report) {
        try {
            var startNanos = System.nanoTime();
            botUpdatesClient.sendProcessingFailureReport(report.description(), report.chatIds());
            scrapperMetricsService.recordRequestDuration(
                    "bot_transport", "processing_failure_report", System.nanoTime() - startNanos);
            LOGGER.atInfo()
                    .addKeyValue("operation", "sendFailureReport")
                    .addKeyValue("chatIdsCount", report.chatIds().size())
                    .addKeyValue("success", true)
                    .log("Failure report sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "sendFailureReport")
                    .addKeyValue("chatIdsCount", report.chatIds().size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Failed links report delivery failed");
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
