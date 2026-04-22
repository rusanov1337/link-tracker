package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.repository.support.PageValidationSupport;
import backend.academy.linktracker.scrapper.repository.support.SupportedLinkCanonicalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
@Transactional
public class OrmTrackedLinkRepository implements TrackedLinkRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public TrackedLink create(URI url, Instant now) {
        var canonicalUrl = SupportedLinkCanonicalizer.canonicalize(url);
        entityManager
                .createNativeQuery("""
                        insert into links (url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor)
                        values (:url, :createdAt, :lastCheckedAt, :lastUpdatedAt, :lastEventAt, :lastEventCursor)
                        on conflict (url) do nothing
                        """)
                .setParameter("url", canonicalUrl.toString())
                .setParameter("createdAt", Timestamp.from(now))
                .setParameter("lastCheckedAt", Timestamp.from(now))
                .setParameter("lastUpdatedAt", Timestamp.from(now))
                .setParameter("lastEventAt", null)
                .setParameter("lastEventCursor", null)
                .executeUpdate();
        return findEntityByUrl(canonicalUrl).map(this::toDomain).orElseThrow();
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
    public List<TrackedLink> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return entityManager
                .createQuery("select l from LinkEntity l where l.id in :ids order by l.id", LinkEntity.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TrackedLink> lockNextPageToCheck(Instant checkedBefore, int limit) {
        @SuppressWarnings("unchecked")
        var rows = (List<Object[]>) entityManager
                .createNativeQuery("""
                        select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                        from links
                        where last_checked_at < :checkedBefore
                        order by id
                        for update skip locked
                        limit :limit
                        """)
                .setParameter("checkedBefore", checkedBefore)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream().map(this::mapTrackedLink).toList();
    }

    @Override
    public List<TrackedLink> claimNextPageToCheck(
            Instant checkedBefore, String processingOwner, Instant claimedAt, Instant processingUntil, int limit) {
        @SuppressWarnings("unchecked")
        var rows = (List<Object[]>) entityManager
                .createNativeQuery("""
                        with claimed as (
                            select id
                            from links
                            where last_checked_at < :checkedBefore
                              and (processing_until is null or processing_until <= :claimedAt)
                            order by id
                            for update skip locked
                            limit :limit
                        )
                        update links
                        set processing_owner = :processingOwner,
                            processing_until = :processingUntil
                        from claimed
                        where links.id = claimed.id
                        returning links.id,
                                  links.url,
                                  links.created_at,
                                  links.last_checked_at,
                                  links.last_updated_at,
                                  links.last_event_at,
                                  links.last_event_cursor
                        """)
                .setParameter("checkedBefore", Timestamp.from(checkedBefore))
                .setParameter("claimedAt", Timestamp.from(claimedAt))
                .setParameter("processingOwner", processingOwner)
                .setParameter("processingUntil", Timestamp.from(processingUntil))
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream().map(this::mapTrackedLink).toList();
    }

    @Override
    public List<TrackedLink> findPageToCheck(Instant checkedBefore, long afterId, int limit) {
        return entityManager
                .createQuery("""
                        select l
                        from LinkEntity l
                        where l.lastCheckedAt <= :checkedBefore
                          and l.id > :afterId
                        order by l.id
                        """, LinkEntity.class)
                .setParameter("checkedBefore", checkedBefore)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TrackedLink> findAll(int limit, int offset) {
        PageValidationSupport.validatePage(limit, offset);

        return entityManager
                .createQuery("select l from LinkEntity l order by l.id", LinkEntity.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
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
        entity.setLastEventAt(trackedLink.lastEventAt());
        entity.setLastEventCursor(trackedLink.lastEventCursor());
    }

    @Override
    public boolean updateIfProcessingOwner(TrackedLink trackedLink, String processingOwner) {
        return entityManager
                        .createNativeQuery("""
                        update links
                        set url = :url,
                            created_at = :createdAt,
                            last_checked_at = :lastCheckedAt,
                            last_updated_at = :lastUpdatedAt,
                            last_event_at = :lastEventAt,
                            last_event_cursor = :lastEventCursor,
                            processing_owner = null,
                            processing_until = null
                        where id = :id and processing_owner = :processingOwner
                        """)
                        .setParameter("id", trackedLink.id())
                        .setParameter(
                                "url",
                                SupportedLinkCanonicalizer.canonicalize(trackedLink.url())
                                        .toString())
                        .setParameter("createdAt", Timestamp.from(trackedLink.createdAt()))
                        .setParameter("lastCheckedAt", Timestamp.from(trackedLink.lastCheckedAt()))
                        .setParameter("lastUpdatedAt", Timestamp.from(trackedLink.lastUpdatedAt()))
                        .setParameter(
                                "lastEventAt",
                                trackedLink.lastEventAt() == null ? null : Timestamp.from(trackedLink.lastEventAt()))
                        .setParameter("lastEventCursor", trackedLink.lastEventCursor())
                        .setParameter("processingOwner", processingOwner)
                        .executeUpdate()
                == 1;
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
    public boolean deleteIfNoSubscriptions(long id) {
        return entityManager.createNativeQuery("""
                        delete from links
                        where id = :id
                          and not exists (
                              select 1
                              from subscriptions
                              where link_id = :id
                          )
                        """).setParameter("id", id).executeUpdate() == 1;
    }

    @Override
    public long count() {
        return entityManager
                .createQuery("select count(l) from LinkEntity l", Long.class)
                .getSingleResult();
    }

    private Optional<LinkEntity> findEntityByUrl(URI canonicalUrl) {
        var links = entityManager
                .createQuery("select l from LinkEntity l where l.url = :url", LinkEntity.class)
                .setParameter("url", canonicalUrl.toString())
                .setMaxResults(1)
                .getResultList();
        return links.isEmpty() ? Optional.empty() : Optional.of(links.getFirst());
    }

    private TrackedLink toDomain(LinkEntity entity) {
        return new TrackedLink(
                entity.getId(),
                URI.create(entity.getUrl()),
                entity.getCreatedAt(),
                entity.getLastCheckedAt(),
                entity.getLastUpdatedAt(),
                entity.getLastEventAt(),
                entity.getLastEventCursor());
    }

    private TrackedLink mapTrackedLink(Object[] row) {
        return new TrackedLink(
                ((Number) row[0]).longValue(),
                URI.create((String) row[1]),
                toInstant(row[2]),
                toInstant(row[3]),
                toInstant(row[4]),
                toInstant(row[5]),
                (String) row[6]);
    }

    private Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Timestamp timestamp -> timestamp.toInstant();
            default ->
                throw new IllegalArgumentException(
                        "Unsupported temporal value type: " + value.getClass().getName());
        };
    }
}
