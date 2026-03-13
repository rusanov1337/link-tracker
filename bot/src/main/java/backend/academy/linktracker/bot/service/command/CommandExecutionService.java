package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommandExecutionService {

    private final CommandRegistry commandRegistry;
    private final BotMetricsService botMetricsService;

    public String handle(CommandRequest commandRequest) {
        var handlerOptional = commandRegistry.find(commandRequest.command());
        var commandType = handlerOptional.isPresent() ? "known" : "unknown";
        var commandStatus = handlerOptional.isPresent() ? "success" : "failure";
        var responseType = handlerOptional.isPresent() ? "known-command-message" : "unknown-command-message";
        var handler = handlerOptional.orElseGet(commandRegistry::fallback);
        var response = handler.handle(commandRequest, commandRegistry.supportedCommands());

        botMetricsService.incrementCommandsTotal(commandRequest.command(), commandStatus);
        logHandledCommand(
                commandRequest.command(), commandType, responseType, commandRequest.chatId(), commandRequest.userId());
        return response;
    }

    private void logHandledCommand(String command, String commandType, String responseType, long chatId, Long userId) {
        log.atInfo()
                .addKeyValue("command", command)
                .addKeyValue("commandType", commandType)
                .addKeyValue("responseType", responseType)
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Command handled");
    }
}
