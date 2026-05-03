package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.support.SubscriptionRepositorySupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
@Transactional
public class SqlLinkSubscriptionRepository implements LinkSubscriptionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<SubscriptionKey> subscriptionKeyRowMapper =
            (resultSet, rowNum) -> new SubscriptionKey(resultSet.getLong("chat_id"), resultSet.getLong("link_id"));
    private final RowMapper<SubscriptionValue> subscriptionTagRowMapper = valueRowMapper("tag");
    private final RowMapper<SubscriptionValue> subscriptionFilterRowMapper = valueRowMapper("filter_value");

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
        var subscriptionKey = jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where chat_id = :chatId and link_id = :linkId
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .query(subscriptionKeyRowMapper)
                .optional();
        if (subscriptionKey.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LinkSubscription(chatId, linkId, findTags(chatId, linkId), findFilters(chatId, linkId)));
    }

    @Override
    public List<LinkSubscription> findByChatId(long chatId, int limit, int offset) {
        var keys = findKeysByChatId(chatId, limit, offset);
        return hydrateSubscriptions(keys, findTagValues(keys), findFilterValues(keys));
    }

    @Override
    public List<LinkSubscription> findByLinkId(long linkId, int limit, int offset) {
        var keys = findKeysByLinkId(linkId, limit, offset);
        return hydrateSubscriptions(keys, findTagValues(keys), findFilterValues(keys));
    }

    @Override
    public boolean existsByLinkId(long linkId) {
        return jdbcClient.sql("""
                    select exists(
                        select 1 from subscriptions where link_id = :linkId
                    )
                    """).param("linkId", linkId).query(Boolean.class).single();
    }

    @Override
    public Map<Long, List<Long>> findChatIdsByLinkIds(List<Long> linkIds) {
        if (linkIds.isEmpty()) {
            return Map.of();
        }

        var rows = jdbcClient
                .sql("""
                    select link_id, chat_id
                    from subscriptions
                    where link_id in (:linkIds)
                    order by link_id, chat_id
                    """)
                .param("linkIds", linkIds)
                .query((resultSet, rowNum) ->
                        new LinkSubscriber(resultSet.getLong("link_id"), resultSet.getLong("chat_id")))
                .list();
        return groupSubscriberChatIds(rows);
    }

    @Override
    public List<LinkSubscription> findAll(int limit, int offset) {
        var keys = findAllKeys(limit, offset);
        return hydrateSubscriptions(keys, findTagValues(keys), findFilterValues(keys));
    }

    @Override
    public long count() {
        return jdbcClient
                .sql("select count(*) from subscriptions")
                .query(Long.class)
                .single();
    }

    private List<LinkSubscription> hydrateSubscriptions(
            List<SubscriptionKey> keys, List<SubscriptionValue> tagValues, List<SubscriptionValue> filterValues) {
        if (keys.isEmpty()) {
            return List.of();
        }

        var tagsBySubscription = groupValues(tagValues);
        var filtersBySubscription = groupValues(filterValues);
        var subscriptions = new ArrayList<LinkSubscription>(keys.size());
        for (var key : keys) {
            subscriptions.add(new LinkSubscription(
                    key.chatId(),
                    key.linkId(),
                    tagsBySubscription.getOrDefault(key, List.of()),
                    filtersBySubscription.getOrDefault(key, List.of())));
        }
        return List.copyOf(subscriptions);
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

    private List<SubscriptionKey> findKeysByChatId(long chatId, int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where chat_id = :chatId
                    order by link_id
                    limit :limit offset :offset
                    """)
                .param("chatId", chatId)
                .param("limit", limit)
                .param("offset", offset)
                .query(subscriptionKeyRowMapper)
                .list();
    }

    private List<SubscriptionKey> findKeysByLinkId(long linkId, int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    where link_id = :linkId
                    order by chat_id
                    limit :limit offset :offset
                    """)
                .param("linkId", linkId)
                .param("limit", limit)
                .param("offset", offset)
                .query(subscriptionKeyRowMapper)
                .list();
    }

    private List<SubscriptionKey> findAllKeys(int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        return jdbcClient
                .sql("""
                    select chat_id, link_id
                    from subscriptions
                    order by chat_id, link_id
                    limit :limit offset :offset
                    """)
                .param("limit", limit)
                .param("offset", offset)
                .query(subscriptionKeyRowMapper)
                .list();
    }

    private List<SubscriptionValue> findTagValues(List<SubscriptionKey> keys) {
        return findValues(keys, "subscription_tags", "tag", "tag", subscriptionTagRowMapper);
    }

    private List<SubscriptionValue> findFilterValues(List<SubscriptionKey> keys) {
        return findValues(keys, "subscription_filters", "filter_value", "filter_value", subscriptionFilterRowMapper);
    }

    private List<SubscriptionValue> findValues(
            List<SubscriptionKey> keys,
            String tableName,
            String valueColumn,
            String orderColumn,
            RowMapper<SubscriptionValue> rowMapper) {
        if (keys.isEmpty()) {
            return List.of();
        }

        var sql = new StringBuilder()
                .append("select chat_id, link_id, ")
                .append(valueColumn)
                .append(" from ")
                .append(tableName)
                .append("""
                     join unnest(:chatIds, :linkIds) as key(chat_id, link_id) using (chat_id, link_id)
                    """);
        sql.append(" order by chat_id, link_id, ").append(orderColumn);

        return jdbcClient
                .sql(sql.toString())
                .param(
                        "chatIds",
                        longArray(keys.stream().map(SubscriptionKey::chatId).toArray(Long[]::new)))
                .param(
                        "linkIds",
                        longArray(keys.stream().map(SubscriptionKey::linkId).toArray(Long[]::new)))
                .query(rowMapper)
                .list();
    }

    private SqlArrayValue longArray(Long[] values) {
        return new SqlArrayValue("bigint", (Object[]) values);
    }

    private Map<SubscriptionKey, List<String>> groupValues(List<SubscriptionValue> values) {
        return SubscriptionRepositorySupport.groupValues(values, SubscriptionValue::key, SubscriptionValue::value);
    }

    private Map<Long, List<Long>> groupSubscriberChatIds(List<LinkSubscriber> rows) {
        var grouped = new LinkedHashMap<Long, List<Long>>();
        for (var row : rows) {
            grouped.computeIfAbsent(row.linkId(), ignored -> new ArrayList<>()).add(row.chatId());
        }
        grouped.replaceAll((ignored, chatIds) -> List.copyOf(chatIds));
        return Map.copyOf(grouped);
    }

    private RowMapper<SubscriptionValue> valueRowMapper(String columnName) {
        return (resultSet, rowNum) -> new SubscriptionValue(
                new SubscriptionKey(resultSet.getLong("chat_id"), resultSet.getLong("link_id")),
                resultSet.getString(columnName));
    }

    private void insertTags(LinkSubscription subscription) {
        insertValues("subscription_tags", "tag", subscription.chatId(), subscription.linkId(), subscription.tags());
    }

    private void insertFilters(LinkSubscription subscription) {
        insertValues(
                "subscription_filters",
                "filter_value",
                subscription.chatId(),
                subscription.linkId(),
                subscription.filters());
    }

    private void insertValues(String tableName, String valueColumn, long chatId, long linkId, List<String> values) {
        if (values.isEmpty()) {
            return;
        }

        var sql = new StringBuilder()
                .append("insert into ")
                .append(tableName)
                .append(" (chat_id, link_id, ")
                .append(valueColumn)
                .append(") values ");
        for (var index = 0; index < values.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("(:chatId, :linkId, :value").append(index).append(")");
        }
        sql.append(" on conflict (chat_id, link_id, ").append(valueColumn).append(") do nothing");

        var statement = jdbcClient.sql(sql.toString()).param("chatId", chatId).param("linkId", linkId);
        for (var index = 0; index < values.size(); index++) {
            statement = statement.param("value" + index, values.get(index));
        }
        statement.update();
    }

    private record SubscriptionKey(long chatId, long linkId) {}

    private record SubscriptionValue(SubscriptionKey key, String value) {}

    private record LinkSubscriber(long linkId, long chatId) {}
}
