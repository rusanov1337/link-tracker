package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.util.List;

public record OutboxNotification(
        long id, long trackedLinkId, URI trackedLinkUrl, String description, List<Long> chatIds) {

    public OutboxNotification {
        chatIds = List.copyOf(chatIds);
    }
}
