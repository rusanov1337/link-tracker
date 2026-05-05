package backend.academy.linktracker.bot.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.resilience")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ResilienceProperties {

    @Valid
    private Retry retry = new Retry();

    @Valid
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Retry {

        @Min(1)
        private int maxAttempts = 3;

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration backoff = Duration.ofMillis(500);

        private Set<Integer> retryableStatuses = new HashSet<>(Set.of(500, 502, 503, 504));
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class CircuitBreaker {

        @Min(1)
        private int slidingWindowSize = 10;

        @Min(1)
        private int minimumNumberOfCalls = 5;

        @DecimalMin("1.0")
        @DecimalMax("100.0")
        private float failureRateThreshold = 50.0F;

        @Min(1)
        private int permittedCallsInHalfOpenState = 5;

        @DurationUnit(ChronoUnit.MILLIS)
        private Duration waitDurationInOpenState = Duration.ofSeconds(5);
    }
}
