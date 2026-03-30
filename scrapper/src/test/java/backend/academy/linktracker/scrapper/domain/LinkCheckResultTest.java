package backend.academy.linktracker.scrapper.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkCheckResultTest {

    @Test
    void emptyPreservesCursorAndContainsNoUpdates() {
        var result = LinkCheckResult.empty(Optional.of("cursor-42"));

        assertEquals(Optional.of("cursor-42"), result.cursor());
        assertTrue(result.updates().isEmpty());
        assertEquals(false, result.failed());
    }

    @Test
    void constructorCopiesUpdatesDefensively() {
        var sourceUpdates = new ArrayList<DetectedUpdate>();
        sourceUpdates.add(new DetectedUpdate(
                UpdateProvider.GITHUB,
                UpdateEventType.ISSUE,
                "Issue title",
                "octocat",
                Instant.parse("2025-01-01T00:00:00Z"),
                "Issue preview",
                "123"));

        var result = new LinkCheckResult(Optional.of("cursor-1"), sourceUpdates, false);
        sourceUpdates.clear();

        assertEquals(1, result.updates().size());
        assertEquals(
                List.of(new DetectedUpdate(
                        UpdateProvider.GITHUB,
                        UpdateEventType.ISSUE,
                        "Issue title",
                        "octocat",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        "Issue preview",
                        "123")),
                result.updates());
    }
}
