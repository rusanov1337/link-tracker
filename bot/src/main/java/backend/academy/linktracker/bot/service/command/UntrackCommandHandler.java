package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UntrackCommandHandler implements CommandHandler {

    public static final String COMMAND = "/untrack";
    public static final String DESCRIPTION = "Прекратить отслеживание ссылки";
    public static final String USAGE_RESPONSE = "Укажите ссылку: /untrack <url>";
    public static final String INVALID_LINK_RESPONSE = "Некорректная ссылка.";
    public static final String SUCCESS_RESPONSE = "Ссылка удалена из отслеживания.";
    public static final String NOT_TRACKED_RESPONSE = "Ссылка не отслеживается.";
    public static final String SCRAPPER_UNAVAILABLE_RESPONSE = "Не удалось удалить ссылку. Попробуйте позже.";

    private final ScrapperClient scrapperClient;
    private final LinkInputParser linkInputParser;

    public UntrackCommandHandler(ScrapperClient scrapperClient, LinkInputParser linkInputParser) {
        this.scrapperClient = scrapperClient;
        this.linkInputParser = linkInputParser;
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
    public String handle(CommandRequest request, CommandContext context) {
        var normalizedLink = linkInputParser.parseHttpUrl(request.arguments());
        if (request.arguments().isBlank()) {
            return USAGE_RESPONSE;
        }
        if (normalizedLink.isEmpty()) {
            return INVALID_LINK_RESPONSE;
        }

        try {
            scrapperClient.ensureChatRegistered(request.chatId());
            scrapperClient.removeLink(request.chatId(), normalizedLink.orElseThrow());
            return SUCCESS_RESPONSE;
        } catch (ScrapperClientException exception) {
            if (exception.hasStatus(HttpStatus.NOT_FOUND.value())) {
                return NOT_TRACKED_RESPONSE;
            }
            if (exception.hasStatus(HttpStatus.BAD_REQUEST.value())) {
                return INVALID_LINK_RESPONSE;
            }
            return SCRAPPER_UNAVAILABLE_RESPONSE;
        }
    }

    @Override
    public int order() {
        return 40;
    }
}
