package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.service.OutboxFailureReport;
import backend.academy.linktracker.scrapper.service.OutboxNotification;
import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepository {

    void saveLinkUpdates(List<OutboxNotification> notifications, Instant createdAt);

    void saveProcessingFailureReports(List<OutboxFailureReport> reports, Instant createdAt);

    List<OutboxNotification> claimNextLinkUpdatesToPublish(
            String processingOwner, Instant claimedAt, Instant processingUntil, int limit);

    List<OutboxFailureReport> claimNextProcessingFailureReportsToPublish(
            String processingOwner, Instant claimedAt, Instant processingUntil, int limit);

    void markProcessed(long id, String processingOwner, Instant processedAt);

    void release(long id, String processingOwner);
}
