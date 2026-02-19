package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
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
    private final TelegramBot telegramBot;
    private final BotCommandService botCommandService;

    public TelegramPollingListener(TelegramBot telegramBot, BotCommandService botCommandService) {
        this.telegramBot = telegramBot;
        this.botCommandService = botCommandService;
    }

    @PostConstruct
    void startPolling() {
        try {
            var setMyCommandsResponse = telegramBot.execute(new SetMyCommands(
                    new BotCommand("/start", "Начать работу"), new BotCommand("/help", "Список доступных команд")));
            LOGGER.atInfo()
                    .addKeyValue("operation", "setMyCommands")
                    .addKeyValue("success", setMyCommandsResponse != null && setMyCommandsResponse.isOk())
                    .log("Telegram command menu configured");
        } catch (RuntimeException ignored) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "setMyCommands")
                    .addKeyValue("success", false)
                    .setCause(ignored)
                    .log("Telegram command menu configuration failed");
        }

        LOGGER.atInfo().addKeyValue("pollingEnabled", true).log("Starting telegram polling listener");
        telegramBot.setUpdatesListener(updates -> {
            LOGGER.atDebug().addKeyValue("updatesCount", updates.size()).log("Updates batch received");
            for (var update : updates) {
                botCommandService.createResponse(update).ifPresent(telegramBot::execute);
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    @PreDestroy
    void stopPolling() {
        LOGGER.atInfo().log("Stopping telegram polling listener");
        telegramBot.removeGetUpdatesListener();
    }
}
