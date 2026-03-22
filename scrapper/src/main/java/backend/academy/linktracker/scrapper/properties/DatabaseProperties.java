package backend.academy.linktracker.scrapper.properties;

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

    public enum AccessType {
        SQL,
        ORM
    }
}
