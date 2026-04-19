package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionFilterEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionFilterId;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionId;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionTagId;
import backend.academy.linktracker.scrapper.repository.support.SubscriptionRepositorySupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public List<LinkSubscription> findByChatId(long chatId, int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        var entities = entityManager
                .createQuery(
                        "select s from SubscriptionEntity s where s.id.chatId = :chatId order by s.id.linkId",
                        SubscriptionEntity.class)
                .setParameter("chatId", chatId)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        return hydrateSubscriptions(entities, findTagValues(toKeys(entities)), findFilterValues(toKeys(entities)));
    }

    @Override
    public List<LinkSubscription> findByLinkId(long linkId, int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        var entities = entityManager
                .createQuery(
                        "select s from SubscriptionEntity s where s.id.linkId = :linkId order by s.id.chatId",
                        SubscriptionEntity.class)
                .setParameter("linkId", linkId)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        return hydrateSubscriptions(entities, findTagValues(toKeys(entities)), findFilterValues(toKeys(entities)));
    }

    @Override
    public boolean existsByLinkId(long linkId) {
        return entityManager
                        .createQuery(
                                "select count(s) from SubscriptionEntity s where s.id.linkId = :linkId", Long.class)
                        .setParameter("linkId", linkId)
                        .getSingleResult()
                > 0;
    }

    @Override
    public Map<Long, List<Long>> findChatIdsByLinkIds(List<Long> linkIds) {
        if (linkIds.isEmpty()) {
            return Map.of();
        }

        var rows = entityManager
                .createQuery("""
                    select s.id.linkId, s.id.chatId
                    from SubscriptionEntity s
                    where s.id.linkId in :linkIds
                    order by s.id.linkId, s.id.chatId
                    """, Object[].class)
                .setParameter("linkIds", linkIds)
                .getResultList();
        return groupSubscriberChatIds(rows);
    }

    @Override
    public List<LinkSubscription> findAll(int limit, int offset) {
        SubscriptionRepositorySupport.validatePage(limit, offset);
        var entities = entityManager
                .createQuery(
                        "select s from SubscriptionEntity s order by s.id.chatId, s.id.linkId",
                        SubscriptionEntity.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        return hydrateSubscriptions(entities, findTagValues(toKeys(entities)), findFilterValues(toKeys(entities)));
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

    private List<SubscriptionKey> toKeys(List<SubscriptionEntity> entities) {
        return entities.stream()
                .map(entity -> new SubscriptionKey(
                        entity.getId().getChatId(), entity.getId().getLinkId()))
                .toList();
    }

    private List<LinkSubscription> hydrateSubscriptions(
            List<SubscriptionEntity> entities,
            List<SubscriptionValue> tagValues,
            List<SubscriptionValue> filterValues) {
        if (entities.isEmpty()) {
            return List.of();
        }

        var tagsBySubscription = groupValues(tagValues);
        var filtersBySubscription = groupValues(filterValues);
        var subscriptions = new ArrayList<LinkSubscription>(entities.size());
        for (var entity : entities) {
            var key = new SubscriptionKey(
                    entity.getId().getChatId(), entity.getId().getLinkId());
            subscriptions.add(new LinkSubscription(
                    key.chatId(),
                    key.linkId(),
                    tagsBySubscription.getOrDefault(key, List.of()),
                    filtersBySubscription.getOrDefault(key, List.of())));
        }
        return List.copyOf(subscriptions);
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

    private List<SubscriptionValue> findTagValues(List<SubscriptionKey> keys) {
        return findValues(keys, "SubscriptionTagEntity", "t", "tag");
    }

    private List<SubscriptionValue> findFilterValues(List<SubscriptionKey> keys) {
        return findValues(keys, "SubscriptionFilterEntity", "f", "filterValue");
    }

    private List<SubscriptionValue> findValues(
            List<SubscriptionKey> keys, String entityName, String alias, String valueField) {
        if (keys.isEmpty()) {
            return List.of();
        }

        var jpql = new StringBuilder()
                .append("select ")
                .append(alias)
                .append(".id.chatId, ")
                .append(alias)
                .append(".id.linkId, ")
                .append(alias)
                .append(".id.")
                .append(valueField)
                .append(" from ")
                .append(entityName)
                .append(" ")
                .append(alias)
                .append(" where ");
        for (var index = 0; index < keys.size(); index++) {
            if (index > 0) {
                jpql.append(" or ");
            }
            jpql.append("(")
                    .append(alias)
                    .append(".id.chatId = :chatId")
                    .append(index)
                    .append(" and ")
                    .append(alias)
                    .append(".id.linkId = :linkId")
                    .append(index)
                    .append(")");
        }
        jpql.append(" order by ")
                .append(alias)
                .append(".id.chatId, ")
                .append(alias)
                .append(".id.linkId, ")
                .append(alias)
                .append(".id.")
                .append(valueField);

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        for (var index = 0; index < keys.size(); index++) {
            var key = keys.get(index);
            query.setParameter("chatId" + index, key.chatId());
            query.setParameter("linkId" + index, key.linkId());
        }
        return query.getResultList().stream().map(this::mapValue).toList();
    }

    private Map<SubscriptionKey, List<String>> groupValues(List<SubscriptionValue> values) {
        return SubscriptionRepositorySupport.groupValues(values, SubscriptionValue::key, SubscriptionValue::value);
    }

    private Map<Long, List<Long>> groupSubscriberChatIds(List<Object[]> rows) {
        var grouped = new LinkedHashMap<Long, List<Long>>();
        for (var row : rows) {
            var linkId = ((Number) row[0]).longValue();
            var chatId = ((Number) row[1]).longValue();
            grouped.computeIfAbsent(linkId, ignored -> new ArrayList<>()).add(chatId);
        }
        grouped.replaceAll((ignored, chatIds) -> List.copyOf(chatIds));
        return Map.copyOf(grouped);
    }

    private SubscriptionValue mapValue(Object[] row) {
        return new SubscriptionValue(
                new SubscriptionKey(((Number) row[0]).longValue(), ((Number) row[1]).longValue()), (String) row[2]);
    }

    private record SubscriptionKey(long chatId, long linkId) {}

    private record SubscriptionValue(SubscriptionKey key, String value) {}
}
