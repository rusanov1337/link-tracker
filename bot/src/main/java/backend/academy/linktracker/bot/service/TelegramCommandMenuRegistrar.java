package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.service.command.CommandRegistry;
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
    private final CommandRegistry commandRegistry;

    public TelegramCommandMenuRegistrar(TelegramBot telegramBot, CommandRegistry commandRegistry) {
        this.telegramBot = telegramBot;
        this.commandRegistry = commandRegistry;
    }

    @PostConstruct
    void registerCommands() {
        try {
            var commands = commandRegistry.supportedCommands().stream()
                    .map(command -> new BotCommand(command.command(), command.description()))
                    .toArray(BotCommand[]::new);
            var response = telegramBot.execute(new SetMyCommands(commands));

            if (response.isOk()) {
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
                    .addKeyValue("errorCode", response.errorCode())
                    .addKeyValue("errorDescription", response.description())
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
