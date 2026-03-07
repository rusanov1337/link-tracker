package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrackCommandHandler implements CommandHandler {

    public static final String COMMAND = "/track";
    public static final String DESCRIPTION = "Начать отслеживание ссылки";

    private final TrackDialogService trackDialogService;

    public TrackCommandHandler(TrackDialogService trackDialogService) {
        this.trackDialogService = trackDialogService;
    }

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
        return trackDialogService.start(request.chatId());
    }
}
