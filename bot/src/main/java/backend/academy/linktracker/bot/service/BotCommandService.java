package backend.academy.linktracker.bot.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BotCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotCommandService.class);
    static final String START_COMMAND = "/start";
    static final String HELP_COMMAND = "/help";
    static final String START_MESSAGE = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    static final String HELP_MESSAGE = """
            Доступные команды:
            /start - начать работу с ботом
            /help - показать список доступных команд
            """;
    static final String UNKNOWN_COMMAND_MESSAGE =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    public Optional<SendMessage> createResponse(Update update) {
        var message = update.message();
        if (message == null || message.text() == null || message.chat() == null) {
            LOGGER.atDebug()
                    .addKeyValue("updateId", update.updateId())
                    .addKeyValue("responseType", "none")
                    .addKeyValue("reason", "unsupported-update")
                    .log("Update ignored");
            return Optional.empty();
        }

        var chatId = message.chat().id().longValue();
        Long userId = message.from() == null ? null : message.from().id();

        if (message.text().startsWith(START_COMMAND)) {
            logHandledCommand(START_COMMAND, "start", "start-message", chatId, userId);
            return Optional.of(new SendMessage(chatId, START_MESSAGE));
        }

        if (message.text().startsWith(HELP_COMMAND)) {
            logHandledCommand(HELP_COMMAND, "help", "help-message", chatId, userId);
            return Optional.of(new SendMessage(chatId, HELP_MESSAGE));
        }

        if (message.text().startsWith("/")) {
            logHandledCommand(message.text(), "unknown", "unknown-command-message", chatId, userId);
            return Optional.of(new SendMessage(chatId, UNKNOWN_COMMAND_MESSAGE));
        }

        LOGGER.atDebug()
                .addKeyValue("command", message.text())
                .addKeyValue("commandType", "not-command")
                .addKeyValue("responseType", "none")
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Message ignored");
        return Optional.empty();
    }

    private void logHandledCommand(String command, String commandType, String responseType, long chatId, Long userId) {
        LOGGER.atInfo()
                .addKeyValue("command", command)
                .addKeyValue("commandType", commandType)
                .addKeyValue("responseType", responseType)
                .addKeyValue("chatId", chatId)
                .addKeyValue("userId", userId)
                .log("Command handled");
    }
}
