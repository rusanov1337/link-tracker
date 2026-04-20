package backend.academy.linktracker.scrapper.properties;

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
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Consumer {

        @Positive
        private int maxAttempts = 3;

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration retryBackoff = Duration.ofSeconds(1);
    }
}
