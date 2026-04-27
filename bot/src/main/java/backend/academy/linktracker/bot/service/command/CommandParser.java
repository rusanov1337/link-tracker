package backend.academy.linktracker.bot.service.command;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CommandParser {

    public Optional<CommandRequest> parse(String text, long chatId, Long userId) {
        if (text == null) {
            return Optional.empty();
        }

        var normalizedText = text.strip();
        if (!normalizedText.startsWith("/")) {
            return Optional.empty();
        }

        var commandAndArguments = normalizedText.split("\\s+", 2);
        var commandWithMention = commandAndArguments[0];
        if (commandWithMention.length() == 1) {
            return Optional.empty();
        }
        var arguments = commandAndArguments.length > 1 ? commandAndArguments[1].strip() : "";
        var command = commandWithMention.split("@", 2)[0];
        if (command.length() == 1) {
            return Optional.empty();
        }

        return Optional.of(new CommandRequest(command, arguments, normalizedText, chatId, userId));
    }
}
