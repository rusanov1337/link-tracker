package backend.academy.linktracker.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdatePrioritizationServiceTest {

    private UpdatePrioritizationService prioritizationService;

    @BeforeEach
    void setUp() {
        var properties = new AiAgentProperties();
        properties.getPrioritization().setHighKeywords(List.of("critical", "urgent", "security"));
        properties.getPrioritization().setLowKeywords(List.of("minor", "typo", "docs"));
        prioritizationService = new UpdatePrioritizationService(properties);
    }

    @Test
    void returnsHighForHighKeyword() {
        assertThat(prioritizationService.prioritize("Critical bug fix")).isEqualTo(UpdatePriority.HIGH);
    }

    @Test
    void returnsMediumWhenNoKeywordsMatch() {
        assertThat(prioritizationService.prioritize("Regular update text")).isEqualTo(UpdatePriority.MEDIUM);
    }

    @Test
    void returnsLowForLowKeyword() {
        assertThat(prioritizationService.prioritize("Fix typo in readme")).isEqualTo(UpdatePriority.LOW);
    }

    @Test
    void highKeywordWinsOverLowKeyword() {
        assertThat(prioritizationService.prioritize("Critical docs update")).isEqualTo(UpdatePriority.HIGH);
    }
}
