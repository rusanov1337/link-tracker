package backend.academy.linktracker.scrapper.repository.memory;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTrackedLinkRepository implements TrackedLinkRepository {

    private final AtomicLong idSequence = new AtomicLong(0);
    private final ConcurrentMap<Long, TrackedLink> linksById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByUrl = new ConcurrentHashMap<>();

    @Override
    public synchronized TrackedLink create(URI url, Instant now) {
        var normalizedUrl = url.normalize();
        var key = urlKey(normalizedUrl);
        var existingId = idsByUrl.get(key);
        if (existingId != null) {
            return linksById.get(existingId);
        }

        var id = idSequence.incrementAndGet();
        var trackedLink = TrackedLink.create(id, normalizedUrl, now);
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
        var id = idsByUrl.get(urlKey(url.normalize()));
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(linksById.get(id));
    }

    @Override
    public List<TrackedLink> findAll() {
        return linksById.values().stream()
                .sorted(Comparator.comparingLong(TrackedLink::id))
                .toList();
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
    public synchronized boolean delete(long id) {
        var removed = linksById.remove(id);
        if (removed == null) {
            return false;
        }

        idsByUrl.remove(urlKey(removed.url()));
        return true;
    }

    @Override
    public long count() {
        return linksById.size();
    }

    private String urlKey(URI url) {
        return url.toString();
    }
}
