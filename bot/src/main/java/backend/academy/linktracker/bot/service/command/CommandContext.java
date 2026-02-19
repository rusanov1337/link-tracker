package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;

public record CommandContext(List<BotCommandDefinition> supportedCommands) {}
