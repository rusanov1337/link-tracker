package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CancelCommandHandler implements CommandHandler {

    public static final String COMMAND = "/cancel";
    public static final String DESCRIPTION = "Отменить текущий диалог";

    private final TrackDialogService trackDialogService;

    public CancelCommandHandler(TrackDialogService trackDialogService) {
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
        return trackDialogService.cancel(request.chatId());
    }
}
