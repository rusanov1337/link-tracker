package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class TelegramPollingListener {

    private final TelegramBot telegramBot;
    private final BotCommandService botCommandService;

    public TelegramPollingListener(TelegramBot telegramBot, BotCommandService botCommandService) {
        this.telegramBot = telegramBot;
        this.botCommandService = botCommandService;
    }

    @PostConstruct
    void startPolling() {
        try {
            telegramBot.execute(new SetMyCommands(
                    new BotCommand("/start", "Начать работу"),
                    new BotCommand("/help", "Список доступных команд")));
        } catch (RuntimeException ignored) {
        }

        telegramBot.setUpdatesListener(updates -> {
            for (var update : updates) {
                botCommandService.createResponse(update).ifPresent(telegramBot::execute);
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    @PreDestroy
    void stopPolling() {
        telegramBot.removeGetUpdatesListener();
    }
}
