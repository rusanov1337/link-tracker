package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class CommandRegistry {

    private final Map<String, CommandHandler> handlersByCommand;
    private final CommandHandler fallbackHandler;
    private final List<BotCommandDefinition> supportedCommands;

    public CommandRegistry(List<CommandHandler> handlers, UnknownCommandHandler fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
        var sortedHandlers = handlers.stream()
                .filter(handler -> handler != fallbackHandler)
                .sorted(Comparator.comparing(CommandHandler::command))
                .toList();

        this.handlersByCommand = sortedHandlers.stream()
                .collect(Collectors.toMap(CommandHandler::command, Function.identity(), (left, right) -> {
                    throw new IllegalStateException("Duplicate command handler: " + left.command());
                }));

        this.supportedCommands = sortedHandlers.stream()
                .map(handler -> new BotCommandDefinition(handler.command(), handler.description()))
                .toList();
    }

    public Optional<CommandHandler> find(String command) {
        return Optional.ofNullable(handlersByCommand.get(command));
    }

    public CommandHandler fallback() {
        return fallbackHandler;
    }

    public List<BotCommandDefinition> supportedCommands() {
        return supportedCommands;
    }
}
