package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.scrapper")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ScrapperProperties {

    @NotEmpty
    @URL
    private String baseUrl;
}
