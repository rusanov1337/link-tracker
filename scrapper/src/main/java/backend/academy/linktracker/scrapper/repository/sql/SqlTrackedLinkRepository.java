package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.support.PageValidationSupport;
import backend.academy.linktracker.scrapper.repository.support.SupportedLinkCanonicalizer;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlTrackedLinkRepository implements TrackedLinkRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<TrackedLink> trackedLinkRowMapper = (resultSet, rowNum) -> mapTrackedLink(resultSet);

    public SqlTrackedLinkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public TrackedLink create(URI url, Instant now) {
        var canonicalUrl = SupportedLinkCanonicalizer.canonicalize(url);
        var inserted = jdbcClient
                .sql("""
                    insert into links (url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor)
                    values (:url, :createdAt, :lastCheckedAt, :lastUpdatedAt, :lastEventAt, :lastEventCursor)
                    on conflict (url) do nothing
                    returning id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    """)
                .param("url", canonicalUrl.toString())
                .param("createdAt", Timestamp.from(now))
                .param("lastCheckedAt", Timestamp.from(now))
                .param("lastUpdatedAt", Timestamp.from(now))
                .param("lastEventAt", null)
                .param("lastEventCursor", null)
                .query(trackedLinkRowMapper)
                .optional();
        return inserted.orElseGet(() -> findByUrl(canonicalUrl).orElseThrow());
    }

    @Override
    public Optional<TrackedLink> findById(long id) {
        return jdbcClient.sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    where id = :id
                    """).param("id", id).query(trackedLinkRowMapper).optional();
    }

    @Override
    public Optional<TrackedLink> findByUrl(URI url) {
        var canonicalUrl = SupportedLinkCanonicalizer.canonicalize(url);
        return jdbcClient
                .sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    where url = :url
                    """)
                .param("url", canonicalUrl.toString())
                .query(trackedLinkRowMapper)
                .optional();
    }

    @Override
    public List<TrackedLink> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return jdbcClient.sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    where id in (:ids)
                    order by id
                    """).param("ids", ids).query(trackedLinkRowMapper).list();
    }

    @Override
    @Transactional
    public List<TrackedLink> lockNextPageToCheck(Instant checkedBefore, int limit) {
        return jdbcClient
                .sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    where last_checked_at < :checkedBefore
                    order by id
                    for update skip locked
                    limit :limit
                    """)
                .param("checkedBefore", Timestamp.from(checkedBefore))
                .param("limit", limit)
                .query(trackedLinkRowMapper)
                .list();
    }

    @Override
    @Transactional
    public List<TrackedLink> claimNextPageToCheck(
            Instant checkedBefore, String processingOwner, Instant claimedAt, Instant processingUntil, int limit) {
        return jdbcClient
                .sql("""
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
                .param("checkedBefore", Timestamp.from(checkedBefore))
                .param("claimedAt", Timestamp.from(claimedAt))
                .param("processingOwner", processingOwner)
                .param("processingUntil", Timestamp.from(processingUntil))
                .param("limit", limit)
                .query(trackedLinkRowMapper)
                .list();
    }

    @Override
    public List<TrackedLink> findPageToCheck(Instant checkedBefore, long afterId, int limit) {
        return jdbcClient
                .sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    where last_checked_at <= :checkedBefore
                      and id > :afterId
                    order by id
                    limit :limit
                    """)
                .param("checkedBefore", Timestamp.from(checkedBefore))
                .param("afterId", afterId)
                .param("limit", limit)
                .query(trackedLinkRowMapper)
                .list();
    }

    @Override
    public List<TrackedLink> findAll(int limit, int offset) {
        PageValidationSupport.validatePage(limit, offset);

        return jdbcClient
                .sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    order by id
                    limit :limit offset :offset
                    """)
                .param("limit", limit)
                .param("offset", offset)
                .query(trackedLinkRowMapper)
                .list();
    }

    @Override
    public void update(TrackedLink trackedLink) {
        var updatedRows =
                jdbcClient.sql("""
                    update links
                    set url = :url,
                        created_at = :createdAt,
                        last_checked_at = :lastCheckedAt,
                        last_updated_at = :lastUpdatedAt,
                        last_event_at = :lastEventAt,
                        last_event_cursor = :lastEventCursor
                    where id = :id
                    """).params(trackedLinkParameters(trackedLink)).update();
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Tracked link does not exist: " + trackedLink.id());
        }
    }

    @Override
    public boolean updateIfProcessingOwner(TrackedLink trackedLink, String processingOwner) {
        return jdbcClient
                        .sql("""
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
                        .params(trackedLinkParameters(trackedLink))
                        .param("processingOwner", processingOwner)
                        .update()
                == 1;
    }

    @Override
    public boolean delete(long id) {
        return jdbcClient
                        .sql("delete from links where id = :id")
                        .param("id", id)
                        .update()
                == 1;
    }

    @Override
    public boolean deleteIfNoSubscriptions(long id) {
        return jdbcClient.sql("""
                    delete from links
                    where id = :id
                      and not exists (
                          select 1
                          from subscriptions
                          where link_id = :id
                      )
                    """).param("id", id).update() == 1;
    }

    @Override
    public long count() {
        return jdbcClient.sql("select count(*) from links").query(Long.class).single();
    }

    private TrackedLink mapTrackedLink(ResultSet resultSet) throws SQLException {
        return new TrackedLink(
                resultSet.getLong("id"),
                URI.create(resultSet.getString("url")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("last_checked_at").toInstant(),
                resultSet.getTimestamp("last_updated_at").toInstant(),
                toInstant(resultSet, "last_event_at"),
                resultSet.getString("last_event_cursor"));
    }

    private Map<String, Object> trackedLinkParameters(TrackedLink trackedLink) {
        var parameters = new HashMap<String, Object>();
        parameters.put("id", trackedLink.id());
        parameters.put(
                "url",
                SupportedLinkCanonicalizer.canonicalize(trackedLink.url()).toString());
        parameters.put("createdAt", Timestamp.from(trackedLink.createdAt()));
        parameters.put("lastCheckedAt", Timestamp.from(trackedLink.lastCheckedAt()));
        parameters.put("lastUpdatedAt", Timestamp.from(trackedLink.lastUpdatedAt()));
        parameters.put(
                "lastEventAt", trackedLink.lastEventAt() == null ? null : Timestamp.from(trackedLink.lastEventAt()));
        parameters.put("lastEventCursor", trackedLink.lastEventCursor());
        return parameters;
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
