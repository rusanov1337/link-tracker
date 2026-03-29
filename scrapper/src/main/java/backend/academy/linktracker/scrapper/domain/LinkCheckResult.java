package backend.academy.linktracker.scrapper.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LinkCheckResult(Optional<String> cursor, List<DetectedUpdate> updates) {

    public LinkCheckResult {
        cursor = Objects.requireNonNull(cursor, "cursor");
        updates = List.copyOf(Objects.requireNonNull(updates, "updates"));
    }

    public static LinkCheckResult empty() {
        return new LinkCheckResult(Optional.empty(), List.of());
    }

    public static LinkCheckResult empty(Optional<String> cursor) {
        return new LinkCheckResult(cursor, List.of());
    }
}
