package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.Chat;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlChatRepository implements ChatRepository {

    private final JdbcClient jdbcClient;

    public SqlChatRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean add(long chatId) {
        var chat = Chat.register(chatId, Instant.now());
        return jdbcClient
                        .sql("""
                    insert into chats (id, registered_at)
                    values (:id, :registeredAt)
                    on conflict (id) do nothing
                    """)
                        .param("id", chat.id())
                        .param("registeredAt", Timestamp.from(chat.registeredAt()))
                        .update()
                == 1;
    }

    @Override
    public boolean remove(long chatId) {
        return jdbcClient
                        .sql("delete from chats where id = :id")
                        .param("id", chatId)
                        .update()
                == 1;
    }

    @Override
    public boolean exists(long chatId) {
        return jdbcClient
                .sql("select exists(select 1 from chats where id = :id)")
                .param("id", chatId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Set<Long> findAllIds() {
        return Set.copyOf(jdbcClient
                .sql("select id from chats order by id")
                .query(Long.class)
                .list());
    }
}
