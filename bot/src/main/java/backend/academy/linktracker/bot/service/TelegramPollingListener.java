package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class TelegramPollingListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramPollingListener.class);
    private static final int MAX_SEND_ATTEMPTS = 3;
    private final TelegramBot telegramBot;
    private final BotCommandService botCommandService;
    private final BotMetricsService botMetricsService;

    public TelegramPollingListener(
            TelegramBot telegramBot, BotCommandService botCommandService, BotMetricsService botMetricsService) {
        this.telegramBot = telegramBot;
        this.botCommandService = botCommandService;
        this.botMetricsService = botMetricsService;
    }

    @PostConstruct
    void startPolling() {
        LOGGER.atInfo().addKeyValue("pollingEnabled", true).log("Starting telegram polling listener");
        telegramBot.setUpdatesListener(updates -> {
            LOGGER.atDebug().addKeyValue("updatesCount", updates.size()).log("Updates batch received");
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
            LOGGER.atError()
                    .addKeyValue("operation", "processUpdate")
                    .addKeyValue("updateId", update.updateId())
                    .setCause(exception)
                    .log("Telegram update processing failed");
        }
    }

    private void executeWithRetry(Update update, SendMessage sendMessage) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                var sendResponse = telegramBot.execute(sendMessage);
                if (sendResponse.isOk()) {
                    LOGGER.atInfo()
                            .addKeyValue("operation", "sendMessage")
                            .addKeyValue("updateId", update.updateId())
                            .addKeyValue("attempt", attempt)
                            .addKeyValue("chatId", sendMessage.getChatId())
                            .addKeyValue("success", true)
                            .log("Telegram response sent");
                    return;
                }

                LOGGER.atWarn()
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
                LOGGER.atWarn()
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

        LOGGER.atError()
                .addKeyValue("operation", "sendMessage")
                .addKeyValue("updateId", update.updateId())
                .addKeyValue("chatId", sendMessage.getChatId())
                .addKeyValue("attempts", MAX_SEND_ATTEMPTS)
                .addKeyValue("success", false)
                .log("Telegram response was not sent after retries");
    }

    @PreDestroy
    void stopPolling() {
        LOGGER.atInfo().log("Stopping telegram polling listener");
        telegramBot.removeGetUpdatesListener();
    }
}
