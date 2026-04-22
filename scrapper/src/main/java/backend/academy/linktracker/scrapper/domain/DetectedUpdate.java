package backend.academy.linktracker.scrapper.domain;

import java.time.Instant;
import java.util.Objects;

public record DetectedUpdate(
        UpdateProvider provider,
        UpdateEventType eventType,
        String title,
        String author,
        Instant createdAt,
        String preview,
        String cursor) {

    public DetectedUpdate {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(cursor, "cursor");
    }
}
