package backend.academy.linktracker.scrapper.repository.memory;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.support.SupportedLinkCanonicalizer;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTrackedLinkRepository implements TrackedLinkRepository {

    private final AtomicLong idSequence = new AtomicLong(0);
    private final ConcurrentMap<Long, TrackedLink> linksById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByUrl = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ProcessingLease> processingLeasesByLinkId = new ConcurrentHashMap<>();

    @Override
    public synchronized TrackedLink create(URI url, Instant now) {
        var canonicalUrl = canonicalize(url);
        var key = urlKey(canonicalUrl);
        var existingId = idsByUrl.get(key);
        if (existingId != null) {
            return linksById.get(existingId);
        }

        var id = idSequence.incrementAndGet();
        var trackedLink = TrackedLink.create(id, canonicalUrl, now);
        idsByUrl.put(key, id);
        linksById.put(id, trackedLink);
        return trackedLink;
    }

    @Override
    public Optional<TrackedLink> findById(long id) {
        return Optional.ofNullable(linksById.get(id));
    }

    @Override
    public Optional<TrackedLink> findByUrl(URI url) {
        var id = idsByUrl.get(urlKey(canonicalize(url)));
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(linksById.get(id));
    }

    @Override
    public List<TrackedLink> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        var requestedIds = new HashSet<>(ids);
        return linksById.values().stream()
                .filter(link -> requestedIds.contains(link.id()))
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .toList();
    }

    @Override
    public List<TrackedLink> lockNextPageToCheck(Instant checkedBefore, int limit) {
        return linksById.values().stream()
                .filter(link -> link.lastCheckedAt().isBefore(checkedBefore))
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<TrackedLink> claimNextPageToCheck(
            Instant checkedBefore, String processingOwner, Instant claimedAt, Instant processingUntil, int limit) {
        var claimedLinks = linksById.values().stream()
                .filter(link -> link.lastCheckedAt().isBefore(checkedBefore))
                .filter(link -> isClaimable(link.id(), claimedAt))
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .limit(limit)
                .toList();
        for (var link : claimedLinks) {
            processingLeasesByLinkId.put(link.id(), new ProcessingLease(processingOwner, processingUntil));
        }
        return List.copyOf(claimedLinks);
    }

    @Override
    public List<TrackedLink> findPageToCheck(Instant checkedBefore, long afterId, int limit) {
        return linksById.values().stream()
                .filter(link -> !link.lastCheckedAt().isAfter(checkedBefore))
                .filter(link -> link.id() > afterId)
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TrackedLink> findAll(int limit, int offset) {
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Page offset must be non-negative");
        }

        var sortedLinks = linksById.values().stream()
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .toList();
        if (offset >= sortedLinks.size()) {
            return List.of();
        }

        var toIndex = Math.min(sortedLinks.size(), offset + limit);
        return List.copyOf(sortedLinks.subList(offset, toIndex));
    }

    @Override
    public synchronized void update(TrackedLink trackedLink) {
        var existing = linksById.get(trackedLink.id());
        if (existing == null) {
            throw new IllegalArgumentException("Tracked link does not exist: " + trackedLink.id());
        }

        linksById.put(trackedLink.id(), trackedLink);
        var existingUrlKey = urlKey(existing.url());
        var newUrlKey = urlKey(trackedLink.url());
        if (!existingUrlKey.equals(newUrlKey)) {
            idsByUrl.remove(existingUrlKey);
            idsByUrl.put(newUrlKey, trackedLink.id());
        }
    }

    @Override
    public synchronized boolean updateIfProcessingOwner(TrackedLink trackedLink, String processingOwner) {
        var lease = processingLeasesByLinkId.get(trackedLink.id());
        if (lease == null || !lease.owner().equals(processingOwner)) {
            return false;
        }

        update(trackedLink);
        processingLeasesByLinkId.remove(trackedLink.id());
        return true;
    }

    @Override
    public synchronized boolean delete(long id) {
        var removed = linksById.remove(id);
        if (removed == null) {
            return false;
        }

        idsByUrl.remove(urlKey(removed.url()));
        processingLeasesByLinkId.remove(id);
        return true;
    }

    @Override
    public long count() {
        return linksById.size();
    }

    private String urlKey(URI url) {
        return url.toString();
    }

    private URI canonicalize(URI url) {
        return SupportedLinkCanonicalizer.canonicalize(url);
    }

    private boolean isClaimable(long linkId, Instant claimedAt) {
        var lease = processingLeasesByLinkId.get(linkId);
        return lease == null || !lease.processingUntil().isAfter(claimedAt);
    }

    private record ProcessingLease(String owner, Instant processingUntil) {}
}
