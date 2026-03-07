package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements CommandHandler {

    public static final String COMMAND = "/start";
    public static final String DESCRIPTION = "Начать работу";
    public static final String RESPONSE = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    public static final String SCRAPPER_UNAVAILABLE_RESPONSE = "Не удалось начать работу. Попробуйте позже.";

    private final ScrapperClient scrapperClient;

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
        try {
            scrapperClient.ensureChatRegistered(request.chatId());
            return RESPONSE;
        } catch (ScrapperClientException exception) {
            return SCRAPPER_UNAVAILABLE_RESPONSE;
        }
    }
}
