package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistry {

    private final Map<String, CommandHandler> handlersByCommand;
    private final CommandHandler fallbackHandler;
    private final List<BotCommandDefinition> supportedCommands;

    public CommandRegistry(List<CommandHandler> handlers) {
        this.fallbackHandler = handlers.stream()
                .filter(CommandHandler::isFallback)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fallback command handler is not configured"));

        var sortedHandlers = sortHandlers(handlers);

        this.handlersByCommand = sortedHandlers.stream()
                .filter(handler -> !handler.isFallback())
                .collect(Collectors.toMap(
                        CommandHandler::command,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("Duplicate command handler: " + left.command());
                        },
                        LinkedHashMap::new));

        this.supportedCommands = handlersByCommand.values().stream()
                .map(handler -> new BotCommandDefinition(handler.command(), handler.description()))
                .toList();
    }

    private List<CommandHandler> sortHandlers(List<CommandHandler> handlers) {
        return handlers.stream()
                .sorted((left, right) -> {
                    int orderCompare = Integer.compare(left.order(), right.order());
                    if (orderCompare != 0) {
                        return orderCompare;
                    }

                    return left.command().compareTo(right.command());
                })
                .toList();
    }

    public CommandHandler resolve(String command) {
        return handlersByCommand.getOrDefault(command, fallbackHandler);
    }

    public boolean isKnown(String command) {
        return handlersByCommand.containsKey(command);
    }

    public List<BotCommandDefinition> supportedCommands() {
        return supportedCommands;
    }
}
