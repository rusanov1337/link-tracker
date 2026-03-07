package backend.academy.linktracker.scrapper.repository.memory;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    private URI canonicalize(URI url) {
        var normalized = url.normalize();
        var scheme = normalized.getScheme();
        var host = normalized.getHost();
        if (scheme == null || host == null) {
            return normalized;
        }

        var normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        var normalizedHost = host.toLowerCase(Locale.ROOT);
        var segments = pathSegments(normalized);
        var canonicalPath = canonicalPath(normalizedHost, segments)
                .orElseGet(() -> normalized.getRawPath() == null ? "" : normalized.getRawPath());

        return URI.create(buildAuthority(normalizedScheme, normalized, normalizedHost) + canonicalPath);
    }

    private String buildAuthority(String scheme, URI normalized, String host) {
        var rawAuthority = new StringBuilder(scheme).append("://");
        if (normalized.getRawUserInfo() != null) {
            rawAuthority.append(normalized.getRawUserInfo()).append('@');
        }
        rawAuthority.append(host);
        if (normalized.getPort() >= 0) {
            rawAuthority.append(':').append(normalized.getPort());
        }
        return rawAuthority.toString();
    }

    private Optional<String> canonicalPath(String host, List<String> segments) {
        return switch (host) {
            case "github.com" ->
                segments.size() == 2 ? Optional.of("/" + segments.get(0) + "/" + segments.get(1)) : Optional.empty();
            case "stackoverflow.com" -> canonicalStackoverflowPath(segments);
            default -> Optional.empty();
        };
    }

    private Optional<String> canonicalStackoverflowPath(List<String> segments) {
        if (segments.size() < 2) {
            return Optional.empty();
        }
        if ("questions".equals(segments.getFirst()) && isNumeric(segments.get(1))) {
            return Optional.of("/questions/" + segments.get(1));
        }
        if ("q".equals(segments.getFirst()) && isNumeric(segments.get(1))) {
            return Optional.of("/questions/" + segments.get(1));
        }
        return Optional.empty();
    }

    private boolean isNumeric(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private List<String> pathSegments(URI uri) {
        return Arrays.stream(uri.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }
}
