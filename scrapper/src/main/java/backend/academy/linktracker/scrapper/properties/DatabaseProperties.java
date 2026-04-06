package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.database")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class DatabaseProperties {

    @NotNull
    private AccessType accessType = AccessType.SQL;

    @Min(1)
    private int pageSize = 500;

    public enum AccessType {
        SQL,
        ORM
    }
}
