package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UntrackCommandHandler implements CommandHandler {

    public static final String COMMAND = "/untrack";
    public static final String DESCRIPTION = "Прекратить отслеживание ссылки";
    public static final String USAGE_RESPONSE = "Укажите ссылку: /untrack <url>";
    public static final String INVALID_LINK_RESPONSE = "Некорректная ссылка.";
    public static final String SUCCESS_RESPONSE = "Ссылка удалена из отслеживания.";
    public static final String NOT_TRACKED_RESPONSE = "Ссылка не отслеживается.";
    public static final String START_REQUIRED_RESPONSE = "Сначала выполните /start.";
    public static final String SCRAPPER_UNAVAILABLE_RESPONSE = "Не удалось удалить ссылку. Попробуйте позже.";

    private final ScrapperClient scrapperClient;
    private final LinkInputParser linkInputParser;

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
        var normalizedLink = linkInputParser.parseHttpUrl(request.arguments());
        if (request.arguments().isBlank()) {
            return USAGE_RESPONSE;
        }
        if (normalizedLink.isEmpty()) {
            return INVALID_LINK_RESPONSE;
        }

        try {
            scrapperClient.removeLink(request.chatId(), normalizedLink.orElseThrow());
            return SUCCESS_RESPONSE;
        } catch (ScrapperClientException exception) {
            if (exception.isChatNotFound()) {
                return START_REQUIRED_RESPONSE;
            }
            if (exception.isLinkNotFound()) {
                return NOT_TRACKED_RESPONSE;
            }
            if (exception.hasStatus(HttpStatus.BAD_REQUEST.value())) {
                return INVALID_LINK_RESPONSE;
            }
            return SCRAPPER_UNAVAILABLE_RESPONSE;
        }
    }
}
