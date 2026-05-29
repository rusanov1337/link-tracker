package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.kafka.KafkaNotificationDeliveryException;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinkUpdateNotificationService {

    private final TelegramBot telegramBot;
    private final PendingLinkUpdateStore pendingLinkUpdateStore;
    private final PendingFailureReportStore pendingFailureReportStore;
    private final RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore;
    private final BotMetricsService botMetricsService;

    public void process(LinkUpdate linkUpdate) {
        var pendingUpdates = new ArrayList<PendingLinkUpdate>();
        processLinkUpdate(linkUpdate, pendingUpdates);

        if (!pendingUpdates.isEmpty()) {
            pendingLinkUpdateStore.saveAll(pendingUpdates);
            log.atWarn()
                    .addKeyValue("operation", "queuePendingLinkUpdates")
                    .addKeyValue("pendingUpdatesCount", pendingUpdates.size())
                    .log("Link updates queued for retry");
        }
    }

    public void processStrict(LinkUpdate linkUpdate) {
        var failedDeliveries = processLinkUpdate(linkUpdate, null);

        if (failedDeliveries > 0) {
            throw new KafkaNotificationDeliveryException("Failed to deliver " + failedDeliveries + " link updates");
        }
    }

    public void retryPendingUpdates() {
        var pendingUpdates = pendingLinkUpdateStore.findAll();
        for (var pendingUpdate : pendingUpdates) {
            if (sendUpdate(pendingUpdate)) {
                pendingLinkUpdateStore.remove(pendingUpdate);
                recentlyDeliveredLinkUpdateStore.markDelivered(pendingUpdate);
            }
        }

        var pendingReports = pendingFailureReportStore.findAll();
        for (var pendingReport : pendingReports) {
            if (sendReport(pendingReport)) {
                pendingFailureReportStore.remove(pendingReport);
            }
        }
    }

    public void processReport(ProcessingFailureReport report) {
        var pendingReports = new ArrayList<PendingFailureReport>();
        processFailureReport(report, pendingReports);

        if (!pendingReports.isEmpty()) {
            pendingFailureReportStore.saveAll(pendingReports);
            log.atWarn()
                    .addKeyValue("operation", "queuePendingFailureReports")
                    .addKeyValue("pendingReportsCount", pendingReports.size())
                    .log("Failure reports queued for retry");
        }
    }

    public void processReportStrict(ProcessingFailureReport report) {
        var failedDeliveries = processFailureReport(report, null);

        if (failedDeliveries > 0) {
            throw new KafkaNotificationDeliveryException("Failed to deliver " + failedDeliveries + " failure reports");
        }
    }

    private int processLinkUpdate(LinkUpdate linkUpdate, List<PendingLinkUpdate> pendingUpdates) {
        var failedDeliveries = 0;
        for (var chatId : linkUpdate.tgChatIds()) {
            var pendingUpdate =
                    new PendingLinkUpdate(linkUpdate.id(), chatId, linkUpdate.url(), linkUpdate.description());
            if (skipRecentlyDeliveredUpdate(pendingUpdate)) {
                continue;
            }
            if (!sendUpdate(pendingUpdate)) {
                failedDeliveries++;
                if (pendingUpdates != null) {
                    pendingUpdates.add(pendingUpdate);
                }
                continue;
            }
            recentlyDeliveredLinkUpdateStore.markDelivered(pendingUpdate);
        }
        return failedDeliveries;
    }

    private int processFailureReport(ProcessingFailureReport report, List<PendingFailureReport> pendingReports) {
        var failedDeliveries = 0;
        for (var chatId : report.tgChatIds()) {
            var pendingReport = new PendingFailureReport(chatId, report.description());
            if (sendReport(pendingReport)) {
                continue;
            }
            failedDeliveries++;
            if (pendingReports != null) {
                pendingReports.add(pendingReport);
            }
        }
        return failedDeliveries;
    }

    private boolean skipRecentlyDeliveredUpdate(PendingLinkUpdate pendingUpdate) {
        if (!recentlyDeliveredLinkUpdateStore.wasDeliveredRecently(pendingUpdate)) {
            return false;
        }

        log.atInfo()
                .addKeyValue("operation", "skipDuplicateDeliveredUpdate")
                .addKeyValue("chatId", pendingUpdate.chatId())
                .addKeyValue("updateId", pendingUpdate.updateId())
                .addKeyValue("url", pendingUpdate.url())
                .log("Recently delivered update ignored");
        return true;
    }

    private String buildMessage(PendingLinkUpdate pendingUpdate) {
        return "Обновление по ссылке: " + pendingUpdate.url() + System.lineSeparator() + pendingUpdate.description();
    }

    private boolean sendUpdate(PendingLinkUpdate pendingUpdate) {
        return sendTextMessage(pendingUpdate.chatId(), buildMessage(pendingUpdate), pendingUpdate);
    }

    private boolean sendReport(PendingFailureReport pendingReport) {
        return sendTextMessage(pendingReport.chatId(), pendingReport.description(), null);
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification =
                    "TelegramBot.execute may return null on invalid HTTP responses despite the static signature.")
    private boolean sendTextMessage(long chatId, String text, PendingLinkUpdate pendingUpdate) {
        try {
            var response = telegramBot.execute(new SendMessage(chatId, text));
            if (response == null) {
                logNullResponse(chatId, pendingUpdate);
                return false;
            }
            if (response.isOk()) {
                botMetricsService.incrementSentNotificationsTotal();
                log.atInfo()
                        .addKeyValue("operation", "sendUpdateNotification")
                        .addKeyValue("chatId", chatId)
                        .addKeyValue("updateId", pendingUpdate == null ? null : pendingUpdate.updateId())
                        .addKeyValue("url", pendingUpdate == null ? null : pendingUpdate.url())
                        .addKeyValue("success", true)
                        .log("Update notification sent");
                return true;
            }

            log.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("updateId", pendingUpdate == null ? null : pendingUpdate.updateId())
                    .addKeyValue("url", pendingUpdate == null ? null : pendingUpdate.url())
                    .addKeyValue("errorCode", response.errorCode())
                    .addKeyValue("errorDescription", response.description())
                    .addKeyValue("success", false)
                    .log("Update notification failed");
            return false;
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("updateId", pendingUpdate == null ? null : pendingUpdate.updateId())
                    .addKeyValue("url", pendingUpdate == null ? null : pendingUpdate.url())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Update notification failed with exception");
            return false;
        }
    }

    private void logNullResponse(long chatId, PendingLinkUpdate pendingUpdate) {
        log.atWarn()
                .addKeyValue("operation", "sendUpdateNotification")
                .addKeyValue("chatId", chatId)
                .addKeyValue("updateId", pendingUpdate == null ? null : pendingUpdate.updateId())
                .addKeyValue("url", pendingUpdate == null ? null : pendingUpdate.url())
                .addKeyValue("success", false)
                .log("Update notification failed with null response");
    }
}
