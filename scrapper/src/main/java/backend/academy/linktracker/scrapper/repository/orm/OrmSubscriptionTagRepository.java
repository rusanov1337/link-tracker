package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.repository.SubscriptionTagRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionId;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
@Transactional
public class OrmSubscriptionTagRepository implements SubscriptionTagRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean add(long chatId, long linkId, String tag) {
        var normalizedTag = normalizeTag(tag);
        if (normalizedTag.isEmpty()) {
            return false;
        }

        if (entityManager.find(SubscriptionEntity.class, new SubscriptionId(chatId, linkId)) == null) {
            return false;
        }

        var id = new SubscriptionTagId(chatId, linkId, normalizedTag.orElseThrow());
        if (entityManager.find(SubscriptionTagEntity.class, id) != null) {
            return false;
        }

        entityManager.persist(new SubscriptionTagEntity(id));
        return true;
    }

    @Override
    public boolean update(long chatId, long linkId, String oldTag, String newTag) {
        var normalizedOldTag = normalizeTag(oldTag);
        var normalizedNewTag = normalizeTag(newTag);
        if (normalizedOldTag.isEmpty() || normalizedNewTag.isEmpty()) {
            return false;
        }

        var oldId = new SubscriptionTagId(chatId, linkId, normalizedOldTag.orElseThrow());
        var oldEntity = entityManager.find(SubscriptionTagEntity.class, oldId);
        if (oldEntity == null) {
            return false;
        }

        var newId = new SubscriptionTagId(chatId, linkId, normalizedNewTag.orElseThrow());
        if (oldId.equals(newId)) {
            return true;
        }

        entityManager.remove(oldEntity);
        if (entityManager.find(SubscriptionTagEntity.class, newId) == null) {
            entityManager.persist(new SubscriptionTagEntity(newId));
        }
        entityManager.flush();
        return true;
    }

    @Override
    public boolean remove(long chatId, long linkId, String tag) {
        var normalizedTag = normalizeTag(tag);
        if (normalizedTag.isEmpty()) {
            return false;
        }

        var entity = entityManager.find(
                SubscriptionTagEntity.class, new SubscriptionTagId(chatId, linkId, normalizedTag.orElseThrow()));
        if (entity == null) {
            return false;
        }

        entityManager.remove(entity);
        entityManager.flush();
        return true;
    }

    @Override
    public List<String> findBySubscription(long chatId, long linkId) {
        return entityManager
                .createQuery("""
                        select t.id.tag
                        from SubscriptionTagEntity t
                        where t.id.chatId = :chatId and t.id.linkId = :linkId
                        order by t.id.tag
                        """, String.class)
                .setParameter("chatId", chatId)
                .setParameter("linkId", linkId)
                .getResultList();
    }

    @Override
    public long count() {
        return entityManager
                .createQuery("select count(t) from SubscriptionTagEntity t", Long.class)
                .getSingleResult();
    }

    private Optional<String> normalizeTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }

        var normalized = tag.strip();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }
}
