package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionFilterEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionFilterId;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionId;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
@Transactional
public class OrmLinkSubscriptionRepository implements LinkSubscriptionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean add(LinkSubscription subscription) {
        var id = new SubscriptionId(subscription.chatId(), subscription.linkId());
        if (entityManager.find(SubscriptionEntity.class, id) != null) {
            return false;
        }

        entityManager.persist(new SubscriptionEntity(id, Instant.now()));
        subscription
                .tags()
                .forEach(tag -> entityManager.persist(new SubscriptionTagEntity(
                        new SubscriptionTagId(subscription.chatId(), subscription.linkId(), tag))));
        subscription
                .filters()
                .forEach(filter -> entityManager.persist(new SubscriptionFilterEntity(
                        new SubscriptionFilterId(subscription.chatId(), subscription.linkId(), filter))));
        return true;
    }

    @Override
    public boolean remove(long chatId, long linkId) {
        var id = new SubscriptionId(chatId, linkId);
        var entity = entityManager.find(SubscriptionEntity.class, id);
        if (entity == null) {
            return false;
        }

        entityManager.remove(entity);
        entityManager.flush();
        return true;
    }

    @Override
    public boolean exists(long chatId, long linkId) {
        return entityManager.find(SubscriptionEntity.class, new SubscriptionId(chatId, linkId)) != null;
    }

    @Override
    public Optional<LinkSubscription> find(long chatId, long linkId) {
        return Optional.ofNullable(entityManager.find(SubscriptionEntity.class, new SubscriptionId(chatId, linkId)))
                .map(this::toDomain);
    }

    @Override
    public List<LinkSubscription> findByChatId(long chatId) {
        return entityManager
                .createQuery(
                        "select s from SubscriptionEntity s where s.id.chatId = :chatId order by s.id.linkId",
                        SubscriptionEntity.class)
                .setParameter("chatId", chatId)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<LinkSubscription> findByLinkId(long linkId) {
        return entityManager
                .createQuery(
                        "select s from SubscriptionEntity s where s.id.linkId = :linkId order by s.id.chatId",
                        SubscriptionEntity.class)
                .setParameter("linkId", linkId)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<LinkSubscription> findAll() {
        return entityManager
                .createQuery(
                        "select s from SubscriptionEntity s order by s.id.chatId, s.id.linkId",
                        SubscriptionEntity.class)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return entityManager
                .createQuery("select count(s) from SubscriptionEntity s", Long.class)
                .getSingleResult();
    }

    private LinkSubscription toDomain(SubscriptionEntity entity) {
        var chatId = entity.getId().getChatId();
        var linkId = entity.getId().getLinkId();
        return new LinkSubscription(chatId, linkId, findTags(chatId, linkId), findFilters(chatId, linkId));
    }

    private List<String> findTags(long chatId, long linkId) {
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

    private List<String> findFilters(long chatId, long linkId) {
        return entityManager
                .createQuery("""
                    select f.id.filterValue
                    from SubscriptionFilterEntity f
                    where f.id.chatId = :chatId and f.id.linkId = :linkId
                    order by f.id.filterValue
                    """, String.class)
                .setParameter("chatId", chatId)
                .setParameter("linkId", linkId)
                .getResultList();
    }
}
