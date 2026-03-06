package backend.academy.linktracker.scrapper.repository.memory;

import backend.academy.linktracker.scrapper.domain.Chat;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryChatRepository implements ChatRepository {

    private final ConcurrentMap<Long, Chat> chatsById = new ConcurrentHashMap<>();

    @Override
    public boolean add(long chatId) {
        return chatsById.putIfAbsent(chatId, Chat.register(chatId, Instant.now())) == null;
    }

    @Override
    public boolean remove(long chatId) {
        return chatsById.remove(chatId) != null;
    }

    @Override
    public boolean exists(long chatId) {
        return chatsById.containsKey(chatId);
    }

    @Override
    public Set<Long> findAllIds() {
        return Set.copyOf(new HashSet<>(chatsById.keySet()));
    }
}
