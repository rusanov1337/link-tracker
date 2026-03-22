package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
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
public class SqlLinkSubscriptionRepository implements LinkSubscriptionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<LinkSubscription> subscriptionRowMapper = new RowMapper<>() {
        @Override
        public LinkSubscription mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return mapSubscription(resultSet.getLong("chat_id"), resultSet.getLong("link_id"));
        }
    };

    public SqlLinkSubscriptionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean add(LinkSubscription subscription) {
        var inserted = jdbcClient
                        .sql("""
                    insert into subscriptions (chat_id, link_id, created_at)
                    values (:chatId, :linkId, :createdAt)
                    on conflict (chat_id, link_id) do nothing
                    """)
                        .param("chatId", subscription.chatId())
                        .param("linkId", subscription.linkId())
                        .param("createdAt", Timestamp.from(Instant.now()))
                        .update()
                == 1;
        if (!inserted) {
            return false;
        }

        insertTags(subscription);
        insertFilters(subscription);
        return true;
    }

    @Override
    public boolean remove(long chatId, long linkId) {
        return jdbcClient
                        .sql("""
                    delete from subscriptions
                    where chat_id = :chatId and link_id = :linkId
                    """)
                        .param("chatId", chatId)
                        .param("linkId", linkId)
                        .update()
                == 1;
    }

    @Override
    public boolean exists(long chatId, long linkId) {
        return jdbcClient
                .sql("""
                    select exists(
                        select 1 from subscriptions where chat_id = :chatId and link_id = :linkId
                    )
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<LinkSubscription> find(long chatId, long linkId) {
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where chat_id = :chatId and link_id = :linkId
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .query(subscriptionRowMapper)
                .optional();
    }

    @Override
    public List<LinkSubscription> findByChatId(long chatId) {
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where chat_id = :chatId
                    order by link_id
                    """)
                .param("chatId", chatId)
                .query(subscriptionRowMapper)
                .list();
    }

    @Override
    public List<LinkSubscription> findByLinkId(long linkId) {
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where link_id = :linkId
                    order by chat_id
                    """)
                .param("linkId", linkId)
                .query(subscriptionRowMapper)
                .list();
    }

    @Override
    public List<LinkSubscription> findAll() {
        return jdbcClient.sql("""
                    select chat_id, link_id
                    from subscriptions
                    order by chat_id, link_id
                    """).query(subscriptionRowMapper).list();
    }

    @Override
    public long count() {
        return jdbcClient
                .sql("select count(*) from subscriptions")
                .query(Long.class)
                .single();
    }

    private LinkSubscription mapSubscription(long chatId, long linkId) {
        return new LinkSubscription(chatId, linkId, findTags(chatId, linkId), findFilters(chatId, linkId));
    }

    private List<String> findTags(long chatId, long linkId) {
        return jdbcClient
                .sql("""
                    select tag
                    from subscription_tags
                    where chat_id = :chatId and link_id = :linkId
                    order by tag
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .query(String.class)
                .list();
    }

    private List<String> findFilters(long chatId, long linkId) {
        return jdbcClient
                .sql("""
                    select filter_value
                    from subscription_filters
                    where chat_id = :chatId and link_id = :linkId
                    order by filter_value
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .query(String.class)
                .list();
    }

    private void insertTags(LinkSubscription subscription) {
        for (var tag : subscription.tags()) {
            jdbcClient
                    .sql("""
                        insert into subscription_tags (chat_id, link_id, tag)
                        values (:chatId, :linkId, :tag)
                        on conflict (chat_id, link_id, tag) do nothing
                        """)
                    .param("chatId", subscription.chatId())
                    .param("linkId", subscription.linkId())
                    .param("tag", tag)
                    .update();
        }
    }

    private void insertFilters(LinkSubscription subscription) {
        for (var filter : subscription.filters()) {
            jdbcClient
                    .sql("""
                        insert into subscription_filters (chat_id, link_id, filter_value)
                        values (:chatId, :linkId, :filterValue)
                        on conflict (chat_id, link_id, filter_value) do nothing
                        """)
                    .param("chatId", subscription.chatId())
                    .param("linkId", subscription.linkId())
                    .param("filterValue", filter)
                    .update();
        }
    }
}
