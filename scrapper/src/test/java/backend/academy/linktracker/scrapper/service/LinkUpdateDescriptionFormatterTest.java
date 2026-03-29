package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.domain.UpdateProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkUpdateDescriptionFormatterTest {

    private final LinkUpdateDescriptionFormatter formatter = new LinkUpdateDescriptionFormatter();

    @Test
    void formatUsesStackoverflowLabelsForComments() {
        var message = formatter.format(new DetectedUpdate(
                UpdateProvider.STACKOVERFLOW,
                UpdateEventType.COMMENT,
                "Question title",
                "john",
                Instant.parse("2025-01-01T00:00:00Z"),
                "Comment preview",
                "question-comment:11"));

        assertTrue(message.contains("Новый комментарий"));
        assertTrue(message.contains("Тема: Question title"));
        assertTrue(message.contains("Пользователь: john"));
        assertTrue(message.contains("Превью: Comment preview"));
    }

    @Test
    void formatUsesGithubLabelsForPullRequests() {
        var message = formatter.format(new DetectedUpdate(
                UpdateProvider.GITHUB,
                UpdateEventType.PULL_REQUEST,
                "PR title",
                "octocat",
                Instant.parse("2025-01-01T00:00:00Z"),
                "PR preview",
                "123"));

        assertTrue(message.contains("Новый pull request"));
        assertTrue(message.contains("Название: PR title"));
        assertTrue(message.contains("Описание: PR preview"));
    }
}
