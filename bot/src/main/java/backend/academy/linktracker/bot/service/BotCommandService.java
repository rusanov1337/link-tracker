package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BotCommandService {

    static final String START_COMMAND = "/start";
    static final String HELP_COMMAND = "/help";
    static final String START_MESSAGE = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    static final String HELP_MESSAGE =
            """
            Доступные команды:
            /start - начать работу с ботом
            /help - показать список доступных команд
            """;

    public Optional<SendMessage> createResponse(Update update) {
        var message = update.message();
        if (message == null || message.text() == null || message.chat() == null) {
            return Optional.empty();
        }

        if (message.text().startsWith(START_COMMAND)) {
            return Optional.of(new SendMessage(message.chat().id().longValue(), START_MESSAGE));
        }

        if (message.text().startsWith(HELP_COMMAND)) {
            return Optional.of(new SendMessage(message.chat().id().longValue(), HELP_MESSAGE));
        }

        return Optional.empty();
    }
}
