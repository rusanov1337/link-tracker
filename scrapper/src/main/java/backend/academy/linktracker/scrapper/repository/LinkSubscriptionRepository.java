package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import java.util.List;
import java.util.Optional;

public interface LinkSubscriptionRepository {

    boolean add(LinkSubscription subscription);

    boolean remove(long chatId, long linkId);

    boolean exists(long chatId, long linkId);

    Optional<LinkSubscription> find(long chatId, long linkId);

    List<LinkSubscription> findByChatId(long chatId);

    List<LinkSubscription> findByLinkId(long linkId);

    List<LinkSubscription> findAll();

    long count();
}
