package backend.academy.linktracker.scrapper.repository.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryTrackedLinkRepositoryTest {

    private final InMemoryTrackedLinkRepository repository = new InMemoryTrackedLinkRepository();

    @Test
    void createReturnsSameEntityForDuplicateUrl() {
        var now = Instant.parse("2026-03-06T10:00:00Z");
        var url = URI.create("https://github.com/org/repo");

        var created = repository.create(url, now);
        var duplicate = repository.create(url, now.plusSeconds(5));

        assertEquals(created.id(), duplicate.id());
        assertEquals(1, repository.count());
    }

    @Test
    void updateChangesStoredEntity() {
        var now = Instant.parse("2026-03-06T10:00:00Z");
        var created = repository.create(URI.create("https://stackoverflow.com/questions/1"), now);

        var updated = created.withLastCheckedAt(now.plusSeconds(10)).withLastUpdatedAt(now.plusSeconds(20));
        repository.update(updated);

        var stored = repository.findById(created.id()).orElseThrow();
        assertEquals(updated.lastCheckedAt(), stored.lastCheckedAt());
        assertEquals(updated.lastUpdatedAt(), stored.lastUpdatedAt());
    }

    @Test
    void updateMissingEntityFailsFast() {
        var link = TrackedLink.create(999L, URI.create("https://example.com"), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> repository.update(link));
    }

    @Test
    void deleteRemovesLinkById() {
        var created = repository.create(URI.create("https://github.com/org/another"), Instant.now());

        assertTrue(repository.delete(created.id()));
        assertFalse(repository.delete(created.id()));
        assertTrue(repository.findById(created.id()).isEmpty());
        assertTrue(repository.findByUrl(created.url()).isEmpty());
    }
}
