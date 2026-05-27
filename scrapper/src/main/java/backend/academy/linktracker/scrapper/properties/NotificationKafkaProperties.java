package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
    private Avro avro = new Avro();

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
    public static class Avro {

        @NotBlank
        private String schemaRegistryUrl = "http://localhost:8085";

        private boolean autoRegisterSchemas = true;
    }
}
