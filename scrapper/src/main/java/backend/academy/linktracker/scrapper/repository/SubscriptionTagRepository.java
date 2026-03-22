package backend.academy.linktracker.scrapper.repository;

import java.util.List;

public interface SubscriptionTagRepository {

    boolean add(long chatId, long linkId, String tag);

    boolean update(long chatId, long linkId, String oldTag, String newTag);

    boolean remove(long chatId, long linkId, String tag);

    List<String> findBySubscription(long chatId, long linkId);

    long count();
}
