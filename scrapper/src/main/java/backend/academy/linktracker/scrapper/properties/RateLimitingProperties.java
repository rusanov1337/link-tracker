package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rate-limiting")
@Validated
public record RateLimitingProperties(
        boolean enabled,
        @Min(1) int limitForPeriod,
        @NotNull Duration limitRefreshPeriod,
        @NotNull Duration timeoutDuration) {}
