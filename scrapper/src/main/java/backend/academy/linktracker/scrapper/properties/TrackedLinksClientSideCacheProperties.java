package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache.tracked-links.client-side")
public record TrackedLinksClientSideCacheProperties(
        boolean enabled, @Min(1) int maxSize) {}
