package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.Chat;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.ChatEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class OrmChatRepository implements ChatRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean add(long chatId) {
        if (entityManager.find(ChatEntity.class, chatId) != null) {
            return false;
        }

        var chat = Chat.register(chatId, Instant.now());
        entityManager.persist(new ChatEntity(chat.id(), chat.registeredAt()));
        return true;
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
