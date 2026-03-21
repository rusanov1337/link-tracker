package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class RecentlyDeliveredLinkUpdateStore {

    private final ConcurrentMap<PendingLinkUpdate, Instant> deliveredAtByUpdate = new ConcurrentHashMap<>();
    private final java.time.Duration deduplicationTtl;

    public RecentlyDeliveredLinkUpdateStore(TelegramProperties telegramProperties) {
        this.deduplicationTtl = telegramProperties.getRecentDeliveryDeduplicationTtl();
    }

    public boolean wasDeliveredRecently(PendingLinkUpdate update) {
        var now = Instant.now();
        cleanupExpired(now);
        var deliveredAt = deliveredAtByUpdate.get(update);
        return deliveredAt != null && !deliveredAt.plus(deduplicationTtl).isBefore(now);
    }

    public void markDelivered(PendingLinkUpdate update) {
        var now = Instant.now();
        cleanupExpired(now);
        deliveredAtByUpdate.put(update, now);
    }

    public void clear() {
        deliveredAtByUpdate.clear();
    }

    private void cleanupExpired(Instant now) {
        deliveredAtByUpdate
                .entrySet()
                .removeIf(entry -> entry.getValue().plus(deduplicationTtl).isBefore(now));
    }
}
