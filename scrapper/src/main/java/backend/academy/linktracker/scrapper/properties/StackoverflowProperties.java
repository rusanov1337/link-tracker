package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.stackoverflow")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class StackoverflowProperties {

    @NotEmpty
    private String baseUrl = "https://api.stackexchange.com";

    @NotEmpty
    private String site = "stackoverflow";

    private String key = "";

    private String accessToken = "";

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration connectTimeout = Duration.ofSeconds(2);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration readTimeout = Duration.ofSeconds(5);
}
