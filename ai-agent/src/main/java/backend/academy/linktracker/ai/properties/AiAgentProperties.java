package backend.academy.linktracker.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai-agent")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class AiAgentProperties {

    @Valid
    private Filtering filtering = new Filtering();

    @Valid
    private Summarization summarization = new Summarization();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Filtering {

        private List<String> stopWords = new ArrayList<>(List.of("spam", "ads", "promo"));

        private List<String> excludedAuthors = new ArrayList<>(List.of("bot-user"));

        @Min(0)
        private int minLength = 20;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Summarization {

        @Min(0)
        private int threshold = 500;
    }
}
