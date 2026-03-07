package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HelpCommandHandler implements CommandHandler {

    public static final String COMMAND = "/help";
    public static final String DESCRIPTION = "Список доступных команд";
    private static final String HELP_HEADER = "Доступные команды:";

    @Override
    public String command() {
        return COMMAND;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String handle(CommandRequest request, List<BotCommandDefinition> supportedCommands) {
        var commands = supportedCommands.stream()
                .map(command -> command.command() + " - " + command.description())
                .toList();

        if (commands.isEmpty()) {
            return HELP_HEADER;
        }

        return HELP_HEADER + System.lineSeparator() + String.join(System.lineSeparator(), commands);
    }
}
