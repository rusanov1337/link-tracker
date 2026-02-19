package backend.academy.linktracker.bot.service.command;

import org.springframework.stereotype.Component;

@Component
public class UnknownCommandHandler implements CommandHandler {

    public static final String RESPONSE =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    @Override
    public String command() {
        return "unknown";
    }

    @Override
    public String description() {
        return "Unknown command fallback";
    }

    @Override
    public String handle(CommandRequest request, CommandContext context) {
        return RESPONSE;
    }

    @Override
    public boolean isFallback() {
        return true;
    }
}
