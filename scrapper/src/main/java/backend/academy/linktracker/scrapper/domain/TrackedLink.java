package backend.academy.linktracker.scrapper.domain;

import java.net.URI;
import java.time.Instant;

public record TrackedLink(
        long id,
        URI url,
        Instant createdAt,
        Instant lastCheckedAt,
        Instant lastUpdatedAt,
        Instant lastEventAt,
        String lastEventCursor) {

    public static TrackedLink create(long id, URI url, Instant now) {
        var normalizedUrl = url.normalize();
        return new TrackedLink(id, normalizedUrl, now, now, now, null, null);
    }

    public TrackedLink withLastCheckedAt(Instant checkedAt) {
        return new TrackedLink(id, url, createdAt, checkedAt, lastUpdatedAt, lastEventAt, lastEventCursor);
    }

    public TrackedLink withLastUpdatedAt(Instant updatedAt) {
        return new TrackedLink(id, url, createdAt, lastCheckedAt, updatedAt, lastEventAt, lastEventCursor);
    }

    public TrackedLink withLastEventState(Instant eventAt, String eventCursor) {
        return new TrackedLink(id, url, createdAt, lastCheckedAt, lastUpdatedAt, eventAt, eventCursor);
    }
}
