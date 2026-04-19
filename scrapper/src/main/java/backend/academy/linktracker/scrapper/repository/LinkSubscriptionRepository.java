package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LinkSubscriptionRepository {

    int UNBOUNDED_PAGE_SIZE = Integer.MAX_VALUE;

    boolean add(LinkSubscription subscription);

    boolean remove(long chatId, long linkId);

    boolean exists(long chatId, long linkId);

    Optional<LinkSubscription> find(long chatId, long linkId);

    default List<LinkSubscription> findByChatId(long chatId) {
        return findByChatId(chatId, UNBOUNDED_PAGE_SIZE, 0);
    }

    List<LinkSubscription> findByChatId(long chatId, int limit, int offset);

    default List<LinkSubscription> findByLinkId(long linkId) {
        return findByLinkId(linkId, UNBOUNDED_PAGE_SIZE, 0);
    }

    List<LinkSubscription> findByLinkId(long linkId, int limit, int offset);

    default boolean existsByLinkId(long linkId) {
        return !findByLinkId(linkId, 1, 0).isEmpty();
    }

    Map<Long, List<Long>> findChatIdsByLinkIds(List<Long> linkIds);

    default List<LinkSubscription> findAll() {
        return findAll(UNBOUNDED_PAGE_SIZE, 0);
    }

    List<LinkSubscription> findAll(int limit, int offset);

    long count();
}
