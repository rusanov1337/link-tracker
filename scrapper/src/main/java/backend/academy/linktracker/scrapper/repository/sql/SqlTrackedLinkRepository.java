package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.support.SupportedLinkCanonicalizer;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlTrackedLinkRepository implements TrackedLinkRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<TrackedLink> trackedLinkRowMapper = new RowMapper<>() {
        @Override
        public TrackedLink mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return mapTrackedLink(resultSet);
        }
    };

    public SqlTrackedLinkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public TrackedLink create(URI url, Instant now) {
        var canonicalUrl = SupportedLinkCanonicalizer.canonicalize(url);
        return jdbcClient
                .sql("""
                    insert into links (url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor)
                    values (:url, :createdAt, :lastCheckedAt, :lastUpdatedAt, :lastEventAt, :lastEventCursor)
                    on conflict (url) do update set url = excluded.url
                    returning id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    """)
                .param("url", canonicalUrl.toString())
                .param("createdAt", Timestamp.from(now))
                .param("lastCheckedAt", Timestamp.from(now))
                .param("lastUpdatedAt", Timestamp.from(now))
                .param("lastEventAt", null)
                .param("lastEventCursor", null)
                .query(trackedLinkRowMapper)
                .single();
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
    public List<TrackedLink> findAll() {
        return jdbcClient.sql("""
                    select id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor
                    from links
                    order by id
                    """).query(trackedLinkRowMapper).list();
    }

    @Override
    public void update(TrackedLink trackedLink) {
        var updatedRows = jdbcClient
                .sql("""
                    update links
                    set url = :url,
                        created_at = :createdAt,
                        last_checked_at = :lastCheckedAt,
                        last_updated_at = :lastUpdatedAt,
                        last_event_at = :lastEventAt,
                        last_event_cursor = :lastEventCursor
                    where id = :id
                    """)
                .param("id", trackedLink.id())
                .param(
                        "url",
                        SupportedLinkCanonicalizer.canonicalize(trackedLink.url())
                                .toString())
                .param("createdAt", Timestamp.from(trackedLink.createdAt()))
                .param("lastCheckedAt", Timestamp.from(trackedLink.lastCheckedAt()))
                .param("lastUpdatedAt", Timestamp.from(trackedLink.lastUpdatedAt()))
                .param(
                        "lastEventAt",
                        trackedLink.lastEventAt() == null ? null : Timestamp.from(trackedLink.lastEventAt()))
                .param("lastEventCursor", trackedLink.lastEventCursor())
                .update();
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Tracked link does not exist: " + trackedLink.id());
        }
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

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
