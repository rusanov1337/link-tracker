package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;

public interface CommandHandler {
    String command();

    String description();

    String handle(CommandRequest request, List<BotCommandDefinition> supportedCommands);
}
