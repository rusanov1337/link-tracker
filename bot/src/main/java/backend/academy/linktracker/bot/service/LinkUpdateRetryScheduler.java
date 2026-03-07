package backend.academy.linktracker.bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.telegram",
        name = "delivery-retry-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class LinkUpdateRetryScheduler {

    private final LinkUpdateNotificationService linkUpdateNotificationService;

    @Scheduled(fixedDelayString = "${app.telegram.delivery-retry-interval:60000}")
    public void retryPendingUpdates() {
        linkUpdateNotificationService.retryPendingUpdates();
    }
}
