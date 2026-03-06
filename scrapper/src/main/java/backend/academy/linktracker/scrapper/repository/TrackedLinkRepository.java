package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrackedLinkRepository {

    TrackedLink create(URI url, Instant now);

    Optional<TrackedLink> findById(long id);

    Optional<TrackedLink> findByUrl(URI url);

    List<TrackedLink> findAll();

    void update(TrackedLink trackedLink);

    boolean delete(long id);

    long count();
}
