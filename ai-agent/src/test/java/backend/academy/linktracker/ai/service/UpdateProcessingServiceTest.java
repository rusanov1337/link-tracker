package backend.academy.linktracker.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateProcessingServiceTest {

    private UpdateProcessingService processingService;

    @BeforeEach
    void setUp() {
        var properties = new AiAgentProperties();
        properties.getFiltering().setStopWords(List.of("spam", "ads", "promo"));
        properties.getFiltering().setExcludedAuthors(List.of("bot-user"));
        properties.getFiltering().setMinLength(20);
        properties.getSummarization().setThreshold(30);
        processingService =
                new UpdateProcessingService(new UpdateFilterService(properties), new UpdateSummarizer(properties));
    }

    @Test
    void filtersUpdateByStopWord() {
        var result = processingService.process(update("Useful text with spam marker", "alice"));

        assertThat(result).isEmpty();
    }

    @Test
    void filtersUpdateByStopWordIgnoringCase() {
        var result = processingService.process(update("Useful text with SPAM marker", "alice"));

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFilterStopWordInsideAnotherWord() {
        var result = processingService.process(update("Discussion about antispam protection", "alice"));

        assertThat(result).isPresent();
    }

    @Test
    void filtersUpdateByExcludedAuthor() {
        var result = processingService.process(update("Long enough regular update text", "bot-user"));

        assertThat(result).isEmpty();
    }

    @Test
    void filtersUpdateByMinimumLength() {
        var result = processingService.process(update("too short", "alice"));

        assertThat(result).isEmpty();
    }

    @Test
    void passesValidUpdate() {
        var result = processingService.process(update("Long enough valid text", "alice"));

        assertThat(result).hasValueSatisfying(update -> {
            assertThat(update.id()).isEqualTo(12345L);
            assertThat(update.description()).isEqualTo("Long enough valid text");
            assertThat(update.tgChatIds()).containsExactly(111L, 222L);
            assertThat(update.priority()).isEqualTo(UpdatePriority.HIGH);
        });
    }

    @Test
    void summarizesLongText() {
        var result = processingService.process(update("This update is long enough to require summarization", "alice"));

        assertThat(result).hasValueSatisfying(update -> assertThat(update.description())
                .isEqualTo("This update is long enough to ..."));
    }

    @Test
    void keepsShortTextUnchanged() {
        var result = processingService.process(update("Long enough short text", "alice"));

        assertThat(result)
                .hasValueSatisfying(update -> assertThat(update.description()).isEqualTo("Long enough short text"));
    }

    private RawLinkUpdateEvent update(String description, String author) {
        return new RawLinkUpdateEvent(
                12345L, "https://github.com/octocat/hello-world", description, author, List.of(111L, 222L));
    }
}
