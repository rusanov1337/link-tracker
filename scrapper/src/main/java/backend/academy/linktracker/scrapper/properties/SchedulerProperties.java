package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.scheduler")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class SchedulerProperties {

    private boolean enabled = true;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration interval = Duration.ofMinutes(1);

    @Min(50)
    @Max(500)
    private int batchSize = 500;

    @Min(1)
    private int parallelism = 1;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration processingLease = Duration.ofMinutes(5);
}
