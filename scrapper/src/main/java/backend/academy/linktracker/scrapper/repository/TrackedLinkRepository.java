package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrackedLinkRepository {

    int UNBOUNDED_PAGE_SIZE = Integer.MAX_VALUE;

    TrackedLink create(URI url, Instant now);

    Optional<TrackedLink> findById(long id);

    Optional<TrackedLink> findByUrl(URI url);

    List<TrackedLink> findByIds(List<Long> ids);

    List<TrackedLink> lockNextPageToCheck(Instant checkedBefore, int limit);

    List<TrackedLink> findPageToCheck(Instant checkedBefore, long afterId, int limit);

    default List<TrackedLink> findAll() {
        return findAll(UNBOUNDED_PAGE_SIZE, 0);
    }

    List<TrackedLink> findAll(int limit, int offset);

    void update(TrackedLink trackedLink);

    boolean delete(long id);

    long count();
}
