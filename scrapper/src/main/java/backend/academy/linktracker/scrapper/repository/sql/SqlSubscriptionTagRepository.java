package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.repository.SubscriptionTagRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlSubscriptionTagRepository implements SubscriptionTagRepository {

    private final JdbcClient jdbcClient;

    public SqlSubscriptionTagRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean add(long chatId, long linkId, String tag) {
        var normalizedTag = normalizeTag(tag);
        if (normalizedTag.isEmpty()) {
            return false;
        }

        return jdbcClient
                        .sql("""
                    insert into subscription_tags (chat_id, link_id, tag)
                    values (:chatId, :linkId, :tag)
                    on conflict (chat_id, link_id, tag) do nothing
                    """)
                        .param("chatId", chatId)
                        .param("linkId", linkId)
                        .param("tag", normalizedTag.orElseThrow())
                        .update()
                == 1;
    }

    @Override
    public boolean update(long chatId, long linkId, String oldTag, String newTag) {
        var normalizedOldTag = normalizeTag(oldTag);
        var normalizedNewTag = normalizeTag(newTag);
        if (normalizedOldTag.isEmpty() || normalizedNewTag.isEmpty()) {
            return false;
        }

        var oldValue = normalizedOldTag.orElseThrow();
        var newValue = normalizedNewTag.orElseThrow();
        if (oldValue.equals(newValue)) {
            return exists(chatId, linkId, oldValue);
        }

        var removed = remove(chatId, linkId, oldValue);
        if (!removed) {
            return false;
        }

        add(chatId, linkId, newValue);
        return true;
    }

    @Override
    public boolean remove(long chatId, long linkId, String tag) {
        var normalizedTag = normalizeTag(tag);
        if (normalizedTag.isEmpty()) {
            return false;
        }

        return jdbcClient
                        .sql("""
                    delete from subscription_tags
                    where chat_id = :chatId and link_id = :linkId and tag = :tag
                    """)
                        .param("chatId", chatId)
                        .param("linkId", linkId)
                        .param("tag", normalizedTag.orElseThrow())
                        .update()
                == 1;
    }

    @Override
    public List<String> findBySubscription(long chatId, long linkId) {
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

    @Override
    public long count() {
        return jdbcClient
                .sql("select count(*) from subscription_tags")
                .query(Long.class)
                .single();
    }

    private boolean exists(long chatId, long linkId, String tag) {
        return jdbcClient
                .sql("""
                    select exists(
                        select 1
                        from subscription_tags
                        where chat_id = :chatId and link_id = :linkId and tag = :tag
                    )
                    """)
                .param("chatId", chatId)
                .param("linkId", linkId)
                .param("tag", tag)
                .query(Boolean.class)
                .single();
    }

    private Optional<String> normalizeTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }

        var normalized = tag.strip();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }
}
