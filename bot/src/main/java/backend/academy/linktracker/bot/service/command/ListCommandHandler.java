package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import backend.academy.linktracker.bot.service.BotCommandDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListCommandHandler implements CommandHandler {

    public static final String COMMAND = "/list";
    public static final String DESCRIPTION = "Показать отслеживаемые ссылки";
    public static final String EMPTY_LIST_RESPONSE = "Список отслеживаемых ссылок пуст.";
    public static final String START_REQUIRED_RESPONSE = "Сначала выполните /start.";
    public static final String SCRAPPER_UNAVAILABLE_RESPONSE = "Не удалось получить список ссылок. Попробуйте позже.";

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
            var listResponse = scrapperClient.getLinks(request.chatId());
            var tagFilter = request.arguments().strip();
            var links = listResponse.links() == null ? List.<LinkResponse>of() : listResponse.links();

            if (!tagFilter.isEmpty()) {
                links = links.stream()
                        .filter(link -> link.tags() != null
                                && link.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase(tagFilter)))
                        .toList();
            }

            if (links.isEmpty()) {
                return tagFilter.isEmpty()
                        ? EMPTY_LIST_RESPONSE
                        : "Нет отслеживаемых ссылок с тегом '" + tagFilter + "'.";
            }

            var header =
                    tagFilter.isEmpty() ? "Отслеживаемые ссылки:" : "Отслеживаемые ссылки с тегом '" + tagFilter + "':";
            var formattedLinks = new StringBuilder(header);
            for (int i = 0; i < links.size(); i++) {
                formattedLinks
                        .append(System.lineSeparator())
                        .append(i + 1)
                        .append(". ")
                        .append(links.get(i).url());
            }

            return formattedLinks.toString();
        } catch (ScrapperClientException exception) {
            if (exception.isChatNotFound()) {
                return START_REQUIRED_RESPONSE;
            }
            return SCRAPPER_UNAVAILABLE_RESPONSE;
        }
    }
}
