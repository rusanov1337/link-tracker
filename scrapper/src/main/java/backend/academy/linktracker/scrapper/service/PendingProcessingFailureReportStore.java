package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PendingProcessingFailureReportStore {

    private final Map<Long, Set<URI>> pendingFailedLinksByChat = new ConcurrentHashMap<>();

    public void merge(Map<Long, Set<URI>> failedLinksByChat) {
        for (var entry : failedLinksByChat.entrySet()) {
            pendingFailedLinksByChat
                    .computeIfAbsent(entry.getKey(), ignored -> ConcurrentHashMap.newKeySet())
                    .addAll(entry.getValue());
        }
    }

    public Map<Long, Set<URI>> snapshot() {
        var snapshot = new HashMap<Long, Set<URI>>();
        for (var entry : pendingFailedLinksByChat.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(snapshot);
    }

    public void remove(long chatId, Collection<URI> deliveredUrls) {
        var pendingUrls = pendingFailedLinksByChat.get(chatId);
        if (pendingUrls == null) {
            return;
        }

        pendingUrls.removeAll(deliveredUrls);
        if (pendingUrls.isEmpty()) {
            pendingFailedLinksByChat.remove(chatId, pendingUrls);
        }
    }
}
