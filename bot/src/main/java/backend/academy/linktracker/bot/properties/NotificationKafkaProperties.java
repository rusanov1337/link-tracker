package backend.academy.linktracker.bot.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.kafka")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class NotificationKafkaProperties {

    @Valid
    private Topics topics = new Topics();

    @Valid
    private Consumer consumer = new Consumer();

    @Valid
    private Avro avro = new Avro();

    @Valid
    private AiAgent aiAgent = new AiAgent();

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Topics {

        @NotBlank
        private String linkUpdates = "link-updates";

        @NotBlank
        private String linkUpdatesDlq = "link-updates-dlq";

        @NotBlank
        private String processingFailureReports = "processing-failure-reports";

        @NotBlank
        private String processingFailureReportsDlq = "processing-failure-reports-dlq";

        @NotBlank
        private String processedUpdates = "link.processed-updates";
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Consumer {

        private boolean enabled = true;

        @Positive
        private int maxAttempts = 3;

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration retryBackoff = Duration.ofSeconds(1);
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Avro {

        @NotBlank
        private String schemaRegistryUrl = "http://localhost:8085";

        private boolean autoRegisterSchemas = true;

        private boolean specificReader = true;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class AiAgent {

        private boolean enabled;
    }
}
