package backend.academy.linktracker.scrapper.repository.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
    void createTreatsSchemeAndHostCaseInsensitively() {
        var now = Instant.parse("2026-03-06T10:00:00Z");

        var created = repository.create(URI.create("https://github.com/org/repo"), now);
        var duplicate = repository.create(URI.create("HTTPS://GITHUB.COM/org/repo"), now.plusSeconds(5));

        assertEquals(created.id(), duplicate.id());
        assertEquals("https://github.com/org/repo", created.url().toString());
        assertTrue(
                repository.findByUrl(URI.create("https://GITHUB.COM/org/repo")).isPresent());
        assertEquals(1, repository.count());
    }

    @Test
    void createTreatsEquivalentSupportedUrlsAsDuplicates() {
        var now = Instant.parse("2026-03-06T10:00:00Z");

        var github = repository.create(URI.create("https://github.com/org/repo/"), now);
        var githubDuplicate =
                repository.create(URI.create("https://github.com/org/repo?tab=readme#top"), now.plusSeconds(5));
        var stackoverflow = repository.create(URI.create("https://stackoverflow.com/q/12345/title"), now);
        var stackoverflowDuplicate = repository.create(
                URI.create("https://stackoverflow.com/questions/12345/example?sort=votes#answer"), now.plusSeconds(5));

        assertEquals(github.id(), githubDuplicate.id());
        assertEquals(stackoverflow.id(), stackoverflowDuplicate.id());
        assertEquals("https://github.com/org/repo", github.url().toString());
        assertEquals(
                "https://stackoverflow.com/questions/12345", stackoverflow.url().toString());
        assertTrue(
                repository.findByUrl(URI.create("https://github.com/org/repo/")).isPresent());
        assertTrue(repository
                .findByUrl(URI.create("https://stackoverflow.com/q/12345"))
                .isPresent());
        assertEquals(2, repository.count());
    }

    @Test
    void createTreatsGithubOwnerAndRepoCaseInsensitively() {
        var now = Instant.parse("2026-03-06T10:00:00Z");

        var created = repository.create(URI.create("https://github.com/Org/Repo"), now);
        var duplicate = repository.create(URI.create("https://github.com/org/repo"), now.plusSeconds(5));

        assertEquals(created.id(), duplicate.id());
        assertEquals("https://github.com/org/repo", created.url().toString());
        assertTrue(
                repository.findByUrl(URI.create("https://github.com/ORG/REPO")).isPresent());
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
    void findPageToCheckReturnsLimitedBatch() {
        var first = repository.create(URI.create("https://github.com/org/one"), Instant.parse("2026-03-06T10:00:00Z"));
        var second = repository.create(URI.create("https://github.com/org/two"), Instant.parse("2026-03-06T10:00:01Z"));
        var third =
                repository.create(URI.create("https://github.com/org/three"), Instant.parse("2026-03-06T10:00:02Z"));
        var skipped =
                repository.create(URI.create("https://github.com/org/four"), Instant.parse("2026-03-06T10:00:03Z"));
        repository.update(skipped.withLastCheckedAt(Instant.parse("2026-03-06T10:01:00Z")));

        var firstPage = repository.findPageToCheck(Instant.parse("2026-03-06T10:00:30Z"), 0, 2);
        var secondPage = repository.findPageToCheck(
                Instant.parse("2026-03-06T10:00:30Z"), firstPage.getLast().id(), 2);

        assertEquals(2, firstPage.size());
        assertEquals(
                List.of(first.id(), second.id()),
                firstPage.stream().map(TrackedLink::id).toList());
        assertEquals(
                List.of(third.id()), secondPage.stream().map(TrackedLink::id).toList());
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
