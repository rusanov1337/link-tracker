package backend.academy.linktracker.bot.service.command;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackDialogService {

    public static final String CANCEL_COMMAND = "/cancel";
    public static final String START_PROMPT = "Отправьте ссылку, которую нужно отслеживать.";
    public static final String LINK_INVALID_RESPONSE = "Некорректная ссылка. Отправьте корректный URL или /cancel.";
    public static final String TAGS_PROMPT =
            "Укажите теги через запятую (например: работа, баг) или '-' чтобы пропустить.";
    public static final String FILTERS_PROMPT =
            "Укажите фильтры через запятую (например: pr, issue) или '-' чтобы пропустить.";
    public static final String SUCCESS_RESPONSE = "Ссылка добавлена в отслеживание.";
    public static final String DUPLICATE_RESPONSE = "Ссылка уже отслеживается";
    public static final String CANCELLED_RESPONSE = "Процесс отслеживания отменен.";
    public static final String NOTHING_TO_CANCEL_RESPONSE = "Нет активного процесса отслеживания.";
    public static final String START_REQUIRED_RESPONSE = "Сначала выполните /start.";
    public static final String SCRAPPER_UNAVAILABLE_RESPONSE = "Не удалось добавить ссылку. Попробуйте позже.";

    private final ScrapperClient scrapperClient;
    private final LinkInputParser linkInputParser;
    private final ConcurrentMap<Long, TrackDialogSession> sessionsByChatId = new ConcurrentHashMap<>();

    public String start(long chatId) {
        sessionsByChatId.put(chatId, TrackDialogSession.start());
        log.atInfo()
                .addKeyValue("operation", "trackDialogStart")
                .addKeyValue("chatId", chatId)
                .log("Dialog started");
        return START_PROMPT;
    }

    public boolean isActive(long chatId) {
        return sessionsByChatId.containsKey(chatId);
    }

    public String cancel(long chatId) {
        var removed = sessionsByChatId.remove(chatId);
        if (removed == null) {
            return NOTHING_TO_CANCEL_RESPONSE;
        }

        log.atInfo()
                .addKeyValue("operation", "trackDialogCancel")
                .addKeyValue("chatId", chatId)
                .addKeyValue("state", removed.state())
                .log("Dialog canceled");
        return CANCELLED_RESPONSE;
    }

    public Optional<String> handleUserInput(long chatId, String text) {
        var session = sessionsByChatId.get(chatId);
        if (session == null) {
            return Optional.empty();
        }

        return switch (session.state()) {
            case AWAIT_LINK -> handleLinkInput(chatId, text, session);
            case AWAIT_TAGS -> handleTagsInput(chatId, text, session);
            case AWAIT_FILTERS -> handleFiltersInput(chatId, text, session);
        };
    }

    private Optional<String> handleLinkInput(long chatId, String text, TrackDialogSession session) {
        var normalizedLink = linkInputParser.parseHttpUrl(text);
        if (normalizedLink.isEmpty()) {
            return Optional.of(LINK_INVALID_RESPONSE);
        }

        sessionsByChatId.put(chatId, session.withLink(normalizedLink.orElseThrow()));
        return Optional.of(TAGS_PROMPT);
    }

    private Optional<String> handleTagsInput(long chatId, String text, TrackDialogSession session) {
        var tags = parseCommaSeparatedValues(text);
        sessionsByChatId.put(chatId, session.withTags(tags));
        return Optional.of(FILTERS_PROMPT);
    }

    private Optional<String> handleFiltersInput(long chatId, String text, TrackDialogSession session) {
        var filters = parseCommaSeparatedValues(text);
        try {
            scrapperClient.addLink(chatId, session.link(), session.tags(), filters);
            sessionsByChatId.remove(chatId);
            return Optional.of(SUCCESS_RESPONSE);
        } catch (ScrapperClientException exception) {
            sessionsByChatId.remove(chatId);
            if (exception.isLinkAlreadyTracked()) {
                return Optional.of(DUPLICATE_RESPONSE);
            }
            if (exception.hasStatus(HttpStatus.BAD_REQUEST.value())) {
                return Optional.of(LINK_INVALID_RESPONSE);
            }
            if (exception.isChatNotFound()) {
                return Optional.of(START_REQUIRED_RESPONSE);
            }
            return Optional.of(SCRAPPER_UNAVAILABLE_RESPONSE);
        }
    }

    private List<String> parseCommaSeparatedValues(String text) {
        if (text == null) {
            return List.of();
        }

        var normalized = text.strip();
        if (normalized.isEmpty() || normalized.equals("-")) {
            return List.of();
        }

        return Arrays.stream(normalized.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
