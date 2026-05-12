package backend.academy.linktracker.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai-agent.kafka")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class AiAgentKafkaProperties {

    @Valid
    private Topics topics = new Topics();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Topics {

        @NotBlank
        private String rawUpdates = "link.raw-updates";

        @NotBlank
        private String processedUpdates = "link.processed-updates";
    }
}
