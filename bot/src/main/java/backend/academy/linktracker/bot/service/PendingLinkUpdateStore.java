package backend.academy.linktracker.bot.service;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

@Component
public class PendingLinkUpdateStore {

    private final Queue<PendingLinkUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();

    public void saveAll(List<PendingLinkUpdate> updates) {
        pendingUpdates.addAll(updates);
    }

    public List<PendingLinkUpdate> findAll() {
        return pendingUpdates.stream().toList();
    }

    public void remove(PendingLinkUpdate update) {
        pendingUpdates.remove(update);
    }

    public int size() {
        return pendingUpdates.size();
    }
}
