package backend.academy.linktracker.scrapper.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LinkCheckResult(Optional<String> cursor, List<DetectedUpdate> updates, boolean failed) {

    public LinkCheckResult {
        Objects.requireNonNull(cursor, "cursor");
        updates = List.copyOf(Objects.requireNonNull(updates, "updates"));
    }

    public LinkCheckResult(Optional<String> cursor, List<DetectedUpdate> updates) {
        this(cursor, updates, false);
    }

    public static LinkCheckResult empty() {
        return new LinkCheckResult(Optional.empty(), List.of(), false);
    }

    public static LinkCheckResult empty(Optional<String> cursor) {
        return new LinkCheckResult(cursor, List.of(), false);
    }

    public static LinkCheckResult failure() {
        return new LinkCheckResult(Optional.empty(), List.of(), true);
    }
}
