package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.telegram",
        name = "set-my-commands-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TelegramCommandMenuRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramCommandMenuRegistrar.class);
    private final TelegramBot telegramBot;
    private final BotCommandService botCommandService;

    public TelegramCommandMenuRegistrar(TelegramBot telegramBot, BotCommandService botCommandService) {
        this.telegramBot = telegramBot;
        this.botCommandService = botCommandService;
    }

    @PostConstruct
    void registerCommands() {
        try {
            var commands = botCommandService.supportedCommands().stream()
                    .map(command -> new BotCommand(command.command(), command.description()))
                    .toArray(BotCommand[]::new);
            var response = telegramBot.execute(new SetMyCommands(commands));

            if (response != null && response.isOk()) {
                LOGGER.atInfo()
                        .addKeyValue("operation", "setMyCommands")
                        .addKeyValue("success", true)
                        .addKeyValue("commandsCount", commands.length)
                        .log("Telegram command menu configured");
                return;
            }

            LOGGER.atWarn()
                    .addKeyValue("operation", "setMyCommands")
                    .addKeyValue("success", false)
                    .addKeyValue("commandsCount", commands.length)
                    .addKeyValue("errorCode", response == null ? null : response.errorCode())
                    .addKeyValue("errorDescription", response == null ? "null response" : response.description())
                    .log("Telegram command menu configuration failed");
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "setMyCommands")
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Telegram command menu configuration failed with exception");
        }
    }
}
