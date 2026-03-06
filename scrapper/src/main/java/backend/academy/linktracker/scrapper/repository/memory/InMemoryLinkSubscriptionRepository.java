package backend.academy.linktracker.scrapper.repository.memory;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryLinkSubscriptionRepository implements LinkSubscriptionRepository {

    private final ConcurrentMap<SubscriptionKey, LinkSubscription> subscriptionsByKey = new ConcurrentHashMap<>();

    @Override
    public boolean add(LinkSubscription subscription) {
        return subscriptionsByKey.putIfAbsent(key(subscription.chatId(), subscription.linkId()), subscription) == null;
    }

    @Override
    public boolean remove(long chatId, long linkId) {
        return subscriptionsByKey.remove(key(chatId, linkId)) != null;
    }

    @Override
    public boolean exists(long chatId, long linkId) {
        return subscriptionsByKey.containsKey(key(chatId, linkId));
    }

    @Override
    public Optional<LinkSubscription> find(long chatId, long linkId) {
        return Optional.ofNullable(subscriptionsByKey.get(key(chatId, linkId)));
    }

    @Override
    public List<LinkSubscription> findByChatId(long chatId) {
        return subscriptionsByKey.values().stream()
                .filter(subscription -> subscription.chatId() == chatId)
                .sorted(Comparator.comparingLong(LinkSubscription::linkId))
                .toList();
    }

    @Override
    public List<LinkSubscription> findByLinkId(long linkId) {
        return subscriptionsByKey.values().stream()
                .filter(subscription -> subscription.linkId() == linkId)
                .sorted(Comparator.comparingLong(LinkSubscription::chatId))
                .toList();
    }

    @Override
    public List<LinkSubscription> findAll() {
        return subscriptionsByKey.values().stream()
                .sorted(Comparator.comparingLong(LinkSubscription::chatId).thenComparingLong(LinkSubscription::linkId))
                .toList();
    }

    @Override
    public long count() {
        return subscriptionsByKey.size();
    }

    private SubscriptionKey key(long chatId, long linkId) {
        return new SubscriptionKey(chatId, linkId);
    }

    private record SubscriptionKey(long chatId, long linkId) {}
}
