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
        if (normalizedText.isEmpty() || !normalizedText.startsWith("/")) {
            return Optional.empty();
        }

        int firstSpaceIndex = normalizedText.indexOf(' ');
        var commandWithMention = firstSpaceIndex >= 0 ? normalizedText.substring(0, firstSpaceIndex) : normalizedText;
        var arguments = firstSpaceIndex >= 0
                ? normalizedText.substring(firstSpaceIndex + 1).strip()
                : "";

        int mentionSeparatorIndex = commandWithMention.indexOf('@');
        var command = mentionSeparatorIndex >= 0
                ? commandWithMention.substring(0, mentionSeparatorIndex)
                : commandWithMention;

        if (command.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new CommandRequest(command, arguments, normalizedText, chatId, userId));
    }
}
