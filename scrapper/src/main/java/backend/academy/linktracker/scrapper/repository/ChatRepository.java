package backend.academy.linktracker.scrapper.repository;

import java.util.Set;

public interface ChatRepository {

    boolean add(long chatId);

    boolean remove(long chatId);

    boolean exists(long chatId);

    Set<Long> findAllIds();
}
