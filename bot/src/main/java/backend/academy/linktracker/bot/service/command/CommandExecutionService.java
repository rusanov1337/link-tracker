package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import backend.academy.linktracker.bot.service.BotMetricsService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommandExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutionService.class);

    private final CommandRegistry commandRegistry;
    private final BotMetricsService botMetricsService;
    private final CommandContext commandContext;

    public CommandExecutionService(CommandRegistry commandRegistry, BotMetricsService botMetricsService) {
        this.commandRegistry = commandRegistry;
        this.botMetricsService = botMetricsService;
        this.commandContext = new CommandContext(commandRegistry.supportedCommands());
    }

    public List<BotCommandDefinition> supportedCommands() {
        return commandRegistry.supportedCommands();
    }

    public String handle(CommandRequest commandRequest) {
        var isKnownCommand = commandRegistry.isKnown(commandRequest.command());
        var responseType = isKnownCommand ? "known-command-message" : "unknown-command-message";
        var commandType = isKnownCommand ? "known" : "unknown";
        var handler = commandRegistry.resolve(commandRequest.command());
        var response = handler.handle(commandRequest, commandContext);

        botMetricsService.incrementCommandsTotal(commandType);
        logHandledCommand(
                commandRequest.command(), commandType, responseType, commandRequest.chatId(), commandRequest.userId());
        return response;
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
