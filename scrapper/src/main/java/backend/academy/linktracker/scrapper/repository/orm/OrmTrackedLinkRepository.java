package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.repository.support.SupportedLinkCanonicalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class OrmTrackedLinkRepository implements TrackedLinkRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public TrackedLink create(URI url, Instant now) {
        var canonicalUrl = SupportedLinkCanonicalizer.canonicalize(url);
        var existing = findEntityByUrl(canonicalUrl);
        if (existing.isPresent()) {
            return toDomain(existing.orElseThrow());
        }

        var entity = new LinkEntity(null, canonicalUrl.toString(), now, now, now);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<TrackedLink> findById(long id) {
        return Optional.ofNullable(entityManager.find(LinkEntity.class, id)).map(this::toDomain);
    }

    @Override
    public Optional<TrackedLink> findByUrl(URI url) {
        return findEntityByUrl(SupportedLinkCanonicalizer.canonicalize(url)).map(this::toDomain);
    }

    @Override
    public List<TrackedLink> findAll() {
        return entityManager
                .createQuery("select l from LinkEntity l order by l.id", LinkEntity.class)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void update(TrackedLink trackedLink) {
        var entity = entityManager.find(LinkEntity.class, trackedLink.id());
        if (entity == null) {
            throw new IllegalArgumentException("Tracked link does not exist: " + trackedLink.id());
        }

        entity.setUrl(SupportedLinkCanonicalizer.canonicalize(trackedLink.url()).toString());
        entity.setCreatedAt(trackedLink.createdAt());
        entity.setLastCheckedAt(trackedLink.lastCheckedAt());
        entity.setLastUpdatedAt(trackedLink.lastUpdatedAt());
    }

    @Override
    public boolean delete(long id) {
        var entity = entityManager.find(LinkEntity.class, id);
        if (entity == null) {
            return false;
        }

        entityManager.remove(entity);
        entityManager.flush();
        return true;
    }

    @Override
    public long count() {
        return entityManager
                .createQuery("select count(l) from LinkEntity l", Long.class)
                .getSingleResult();
    }

    private Optional<LinkEntity> findEntityByUrl(URI canonicalUrl) {
        return entityManager
                .createQuery("select l from LinkEntity l where l.url = :url", LinkEntity.class)
                .setParameter("url", canonicalUrl.toString())
                .getResultStream()
                .findFirst();
    }

    private TrackedLink toDomain(LinkEntity entity) {
        return new TrackedLink(
                entity.getId(),
                URI.create(entity.getUrl()),
                entity.getCreatedAt(),
                entity.getLastCheckedAt(),
                entity.getLastUpdatedAt());
    }
}
