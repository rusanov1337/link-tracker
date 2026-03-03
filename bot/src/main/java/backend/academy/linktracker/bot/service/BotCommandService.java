package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.service.command.CommandContext;
import backend.academy.linktracker.bot.service.command.CommandParser;
import backend.academy.linktracker.bot.service.command.CommandRegistry;
import backend.academy.linktracker.bot.service.command.StartCommandHandler;
import backend.academy.linktracker.bot.service.command.UnknownCommandHandler;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BotCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotCommandService.class);
    static final String START_MESSAGE = StartCommandHandler.RESPONSE;
    static final String UNKNOWN_COMMAND_MESSAGE = UnknownCommandHandler.RESPONSE;

    private final CommandParser commandParser;
    private final CommandRegistry commandRegistry;
    private final BotMetricsService botMetricsService;

    public BotCommandService(
            CommandParser commandParser, CommandRegistry commandRegistry, BotMetricsService botMetricsService) {
        this.commandParser = commandParser;
        this.commandRegistry = commandRegistry;
        this.botMetricsService = botMetricsService;
    }

    public List<BotCommandDefinition> supportedCommands() {
        return commandRegistry.supportedCommands();
    }

    public Optional<SendMessage> createResponse(Update update) {
        var message = update.message();
        if (message == null || message.text() == null || message.chat() == null) {
            LOGGER.atDebug()
                    .addKeyValue("updateId", update.updateId())
                    .addKeyValue("responseType", "none")
                    .addKeyValue("reason", "unsupported-update")
                    .log("Update ignored");
            return Optional.empty();
        }

        var chatId = message.chat().id().longValue();
        Long userId = message.from() == null ? null : message.from().id();
        var commandRequest = commandParser.parse(message.text(), chatId, userId);

        if (commandRequest.isPresent()) {
            var parsedCommand = commandRequest.orElseThrow();
            var isKnownCommand = commandRegistry.isKnown(parsedCommand.command());
            var responseType = isKnownCommand ? "known-command-message" : "unknown-command-message";
            var commandType = isKnownCommand ? "known" : "unknown";
            var handler = commandRegistry.resolve(parsedCommand.command());
            var context = new CommandContext(commandRegistry.supportedCommands());
            var response = handler.handle(parsedCommand, context);

            botMetricsService.incrementCommandsTotal(commandType);
            logHandledCommand(parsedCommand.command(), commandType, responseType, chatId, userId);
            return Optional.of(new SendMessage(chatId, response));
        }

        LOGGER.atDebug()
                .addKeyValue("command", message.text())
                .addKeyValue("commandType", "not-command")
                .addKeyValue("responseType", "none")
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Message ignored");
        return Optional.empty();
    }

    private void logHandledCommand(String command, String commandType, String responseType, long chatId, Long userId) {
        LOGGER.atInfo()
                .addKeyValue("command", command)
                .addKeyValue("commandType", commandType)
                .addKeyValue("responseType", responseType)
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Command handled");
    }
}
