package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.service.command.CommandExecutionService;
import backend.academy.linktracker.bot.service.command.CommandParser;
import backend.academy.linktracker.bot.service.command.StartCommandHandler;
import backend.academy.linktracker.bot.service.command.TrackCommandHandler;
import backend.academy.linktracker.bot.service.command.TrackDialogService;
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
    private final CommandExecutionService commandExecutionService;
    private final TrackDialogService trackDialogService;

    public BotCommandService(
            CommandParser commandParser,
            CommandExecutionService commandExecutionService,
            TrackDialogService trackDialogService) {
        this.commandParser = commandParser;
        this.commandExecutionService = commandExecutionService;
        this.trackDialogService = trackDialogService;
    }

    public List<BotCommandDefinition> supportedCommands() {
        return commandExecutionService.supportedCommands();
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
        var messageText = message.text();
        var commandRequest = commandParser.parse(messageText, chatId, userId);

        if (commandRequest.isPresent()) {
            var parsedCommand = commandRequest.orElseThrow();
            if (TrackDialogService.CANCEL_COMMAND.equals(parsedCommand.command())) {
                var cancelResponse = trackDialogService.cancel(chatId);
                return Optional.of(new SendMessage(chatId, cancelResponse));
            }

            if (trackDialogService.isActive(chatId) && !TrackCommandHandler.COMMAND.equals(parsedCommand.command())) {
                trackDialogService.cancel(chatId);
            }

            var response = commandExecutionService.handle(parsedCommand);
            return Optional.of(new SendMessage(chatId, response));
        }

        if (trackDialogService.isActive(chatId)) {
            var response = trackDialogService.handleUserInput(chatId, messageText);
            if (response.isPresent()) {
                return Optional.of(new SendMessage(chatId, response.orElseThrow()));
            }
        }

        LOGGER.atDebug()
                .addKeyValue("command", messageText)
                .addKeyValue("commandType", "not-command")
                .addKeyValue("responseType", "none")
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Message ignored");
        return Optional.empty();
    }
}
