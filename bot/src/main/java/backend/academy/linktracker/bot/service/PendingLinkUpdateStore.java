package backend.academy.linktracker.bot.service;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

@Component
public class PendingLinkUpdateStore {

    private final Queue<PendingLinkUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final Set<PendingLinkUpdate> pendingUpdatesIndex = ConcurrentHashMap.newKeySet();

    public void saveAll(List<PendingLinkUpdate> updates) {
        for (var update : updates) {
            if (pendingUpdatesIndex.add(update)) {
                pendingUpdates.add(update);
            }
        }
    }

    public List<PendingLinkUpdate> findAll() {
        return pendingUpdates.stream().toList();
    }

    public void remove(PendingLinkUpdate update) {
        if (pendingUpdates.remove(update)) {
            pendingUpdatesIndex.remove(update);
        }
    }

    public int size() {
        return pendingUpdatesIndex.size();
    }
}
