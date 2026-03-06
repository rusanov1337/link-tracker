package backend.academy.linktracker.scrapper.domain;

import java.net.URI;
import java.time.Instant;

public record TrackedLink(long id, URI url, Instant createdAt, Instant lastCheckedAt, Instant lastUpdatedAt) {

    public static TrackedLink create(long id, URI url, Instant now) {
        var normalizedUrl = url.normalize();
        return new TrackedLink(id, normalizedUrl, now, now, now);
    }

    public TrackedLink withLastCheckedAt(Instant checkedAt) {
        return new TrackedLink(id, url, createdAt, checkedAt, lastUpdatedAt);
    }

    public TrackedLink withLastUpdatedAt(Instant updatedAt) {
        return new TrackedLink(id, url, createdAt, lastCheckedAt, updatedAt);
    }
}
