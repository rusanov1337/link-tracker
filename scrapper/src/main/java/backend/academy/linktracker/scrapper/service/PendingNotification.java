package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import java.net.URI;
import java.util.List;

record PendingNotification(long trackedLinkId, URI trackedLinkUrl, DetectedUpdate update, List<Long> chatIds) {
    PendingNotification {
        chatIds = List.copyOf(chatIds);
    }
}
