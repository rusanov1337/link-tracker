package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.domain.UpdateProvider;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdateDescriptionFormatter {

    public String format(DetectedUpdate update) {
        var titleLabel = update.provider() == UpdateProvider.STACKOVERFLOW ? "Тема" : "Название";
        var previewLabel = switch (update.eventType()) {
            case ISSUE, PULL_REQUEST -> "Описание";
            case ANSWER, COMMENT -> "Превью";
        };
        return String.join(
                System.lineSeparator(),
                eventLabel(update.eventType()),
                titleLabel + ": " + update.title(),
                "Пользователь: " + update.author(),
                "Создано: " + DateTimeFormatter.ISO_INSTANT.format(update.createdAt()),
                previewLabel + ": " + update.preview());
    }

    private String eventLabel(UpdateEventType eventType) {
        return switch (eventType) {
            case ISSUE -> "Новый issue";
            case PULL_REQUEST -> "Новый pull request";
            case ANSWER -> "Новый ответ";
            case COMMENT -> "Новый комментарий";
        };
    }
}
