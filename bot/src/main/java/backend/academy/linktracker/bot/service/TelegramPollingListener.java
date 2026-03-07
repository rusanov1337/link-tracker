package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class TelegramPollingListener {

    private final TelegramBot telegramBot;
    private final BotCommandService botCommandService;
    private final BotMetricsService botMetricsService;
    private final TelegramProperties telegramProperties;

    @PostConstruct
    void startPolling() {
        log.atInfo().addKeyValue("pollingEnabled", true).log("Starting telegram polling listener");
        telegramBot.setUpdatesListener(updates -> {
            log.atDebug().addKeyValue("updatesCount", updates.size()).log("Updates batch received");
            for (var update : updates) {
                botMetricsService.incrementUpdatesTotal();
                var startNanos = System.nanoTime();
                processUpdateSafely(update);
                botMetricsService.recordProcessingLatency(System.nanoTime() - startNanos);
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void processUpdateSafely(Update update) {
        try {
            botCommandService.createResponse(update).ifPresent(sendMessage -> executeWithRetry(update, sendMessage));
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("operation", "processUpdate")
                    .addKeyValue("updateId", update.updateId())
                    .setCause(exception)
                    .log("Telegram update processing failed");
        }
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification =
                    "TelegramBot.execute may return null on invalid HTTP responses despite the static signature.")
    private void executeWithRetry(Update update, SendMessage sendMessage) {
        for (int attempt = 1; attempt <= telegramProperties.getMaxSendAttempts(); attempt++) {
            try {
                var sendResponse = telegramBot.execute(sendMessage);
                if (sendResponse == null) {
                    logNullSendResponse(update, sendMessage, attempt);
                    botMetricsService.incrementSendFailuresTotal();
                    continue;
                }
                if (sendResponse.isOk()) {
                    log.atInfo()
                            .addKeyValue("operation", "sendMessage")
                            .addKeyValue("updateId", update.updateId())
                            .addKeyValue("attempt", attempt)
                            .addKeyValue("chatId", sendMessage.getChatId())
                            .addKeyValue("success", true)
                            .log("Telegram response sent");
                    return;
                }

                log.atWarn()
                        .addKeyValue("operation", "sendMessage")
                        .addKeyValue("updateId", update.updateId())
                        .addKeyValue("attempt", attempt)
                        .addKeyValue("chatId", sendMessage.getChatId())
                        .addKeyValue("success", false)
                        .addKeyValue("errorCode", sendResponse.errorCode())
                        .addKeyValue("errorDescription", sendResponse.description())
                        .log("Telegram response send failed");
                botMetricsService.incrementSendFailuresTotal();
            } catch (RuntimeException exception) {
                log.atWarn()
                        .addKeyValue("operation", "sendMessage")
                        .addKeyValue("updateId", update.updateId())
                        .addKeyValue("attempt", attempt)
                        .addKeyValue("chatId", sendMessage.getChatId())
                        .addKeyValue("success", false)
                        .setCause(exception)
                        .log("Telegram response send failed with exception");
                botMetricsService.incrementSendFailuresTotal();
            }
        }

        log.atError()
                .addKeyValue("operation", "sendMessage")
                .addKeyValue("updateId", update.updateId())
                .addKeyValue("chatId", sendMessage.getChatId())
                .addKeyValue("attempts", telegramProperties.getMaxSendAttempts())
                .addKeyValue("success", false)
                .log("Telegram response was not sent after retries");
    }

    private void logNullSendResponse(Update update, SendMessage sendMessage, int attempt) {
        log.atWarn()
                .addKeyValue("operation", "sendMessage")
                .addKeyValue("updateId", update.updateId())
                .addKeyValue("attempt", attempt)
                .addKeyValue("chatId", sendMessage.getChatId())
                .addKeyValue("success", false)
                .log("Telegram response send failed with null response");
    }

    @PreDestroy
    void stopPolling() {
        log.atInfo().log("Stopping telegram polling listener");
        telegramBot.removeGetUpdatesListener();
    }
}
