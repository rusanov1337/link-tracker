package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.Chat;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.ChatEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
@Transactional
public class OrmChatRepository implements ChatRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean add(long chatId) {
        var chat = Chat.register(chatId, Instant.now());
        return entityManager
                        .createNativeQuery("""
                                insert into chats (id, registered_at)
                                values (:id, :registeredAt)
                                on conflict (id) do nothing
                                """)
                        .setParameter("id", chat.id())
                        .setParameter("registeredAt", Timestamp.from(chat.registeredAt()))
                        .executeUpdate()
                == 1;
    }

    @Override
    public boolean remove(long chatId) {
        var chat = entityManager.find(ChatEntity.class, chatId);
        if (chat == null) {
            return false;
        }

        entityManager.remove(chat);
        entityManager.flush();
        return true;
    }

    @Override
    public boolean exists(long chatId) {
        return entityManager.find(ChatEntity.class, chatId) != null;
    }

    @Override
    public Set<Long> findAllIds() {
        return Set.copyOf(entityManager
                .createQuery("select c.id from ChatEntity c order by c.id", Long.class)
                .getResultList());
    }
}
