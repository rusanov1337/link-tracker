package backend.academy.linktracker.scrapper.properties;

import static backend.academy.linktracker.scrapper.validation.ValidationPatterns.HTTP_URL;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.bot")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class BotProperties {

    @NotEmpty
    @Pattern(regexp = HTTP_URL)
    private String baseUrl = "http://localhost:8080";

    private Transport transport = Transport.KAFKA;

    @Valid
    private Http http = new Http();

    @Valid
    private Grpc grpc = new Grpc();

    @Valid
    private Fallback fallback = new Fallback();

    public enum Transport {
        HTTP,
        GRPC,
        KAFKA
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Http {

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration connectTimeout = Duration.ofSeconds(2);

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration readTimeout = Duration.ofSeconds(5);
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Grpc {

        @NotEmpty
        private String host = "localhost";

        @Min(1)
        @Max(65535)
        private int port = 8090;

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration deadline = Duration.ofSeconds(3);
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Fallback {

        private boolean kafkaEnabled;
    }
}
