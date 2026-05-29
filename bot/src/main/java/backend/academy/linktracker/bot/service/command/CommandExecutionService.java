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
        var responseType = handlerOptional.isPresent() ? "known-command-message" : "unknown-command-message";
        var metricCommand = handlerOptional.map(CommandHandler::command).orElse("unknown");
        var handler = handlerOptional.orElseGet(commandRegistry::fallback);
        var startNanos = System.nanoTime();
        try {
            var response = handler.handle(commandRequest, commandRegistry.supportedCommands());
            botMetricsService.incrementCommandsTotal(metricCommand, "success");
            logHandledCommand(
                    commandRequest.command(),
                    commandType,
                    responseType,
                    commandRequest.chatId(),
                    commandRequest.userId());
            return response;
        } catch (RuntimeException exception) {
            botMetricsService.incrementCommandsTotal(metricCommand, "failure");
            log.atWarn()
                    .addKeyValue("command", commandRequest.command())
                    .addKeyValue("commandType", commandType)
                    .addKeyValue("chatId", commandRequest.chatId())
                    .addKeyValue("userId", commandRequest.userId())
                    .setCause(exception)
                    .log("Command handling failed");
            throw exception;
        } finally {
            botMetricsService.recordCommandDuration("command", metricCommand, System.nanoTime() - startNanos);
        }
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
