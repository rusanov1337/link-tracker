package backend.academy.linktracker.bot.service.command;

import org.springframework.stereotype.Component;

@Component
public class StartCommandHandler implements CommandHandler {

    public static final String COMMAND = "/start";
    public static final String DESCRIPTION = "Начать работу";
    public static final String RESPONSE = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";

    @Override
    public String command() {
        return COMMAND;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String handle(CommandRequest request, CommandContext context) {
        return RESPONSE;
    }

    @Override
    public int order() {
        return 10;
    }
}
