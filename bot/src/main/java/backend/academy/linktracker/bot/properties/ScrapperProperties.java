package backend.academy.linktracker.bot.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private String baseUrl = "http://localhost:8081";

    private Transport transport = Transport.HTTP;

    @Valid
    private Grpc grpc = new Grpc();

    public enum Transport {
        HTTP,
        GRPC
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
        private int port = 8091;
    }
}
