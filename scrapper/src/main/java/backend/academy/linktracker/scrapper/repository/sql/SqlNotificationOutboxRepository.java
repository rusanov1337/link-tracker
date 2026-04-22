package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.repository.NotificationOutboxRepository;
import backend.academy.linktracker.scrapper.service.OutboxFailureReport;
import backend.academy.linktracker.scrapper.service.OutboxNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SqlNotificationOutboxRepository implements NotificationOutboxRepository {

    private static final String LINK_UPDATE_EVENT_TYPE = "LINK_UPDATE";
    private static final String PROCESSING_FAILURE_REPORT_EVENT_TYPE = "PROCESSING_FAILURE_REPORT";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcClient jdbcClient;
    private final RowMapper<OutboxNotification> outboxNotificationRowMapper =
            (resultSet, rowNum) -> mapOutboxNotification(resultSet);
    private final RowMapper<OutboxFailureReport> outboxFailureReportRowMapper =
            (resultSet, rowNum) -> mapOutboxFailureReport(resultSet);

    public SqlNotificationOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void saveLinkUpdates(List<OutboxNotification> notifications, Instant createdAt) {
        for (var notification : notifications) {
            jdbcClient
                    .sql("""
                        insert into notification_outbox (event_type, payload, created_at)
                        values (:eventType, :payload, :createdAt)
                        """)
                    .param("eventType", LINK_UPDATE_EVENT_TYPE)
                    .param("payload", writePayload(notification))
                    .param("createdAt", Timestamp.from(createdAt))
                    .update();
        }
    }

    @Override
    public void saveProcessingFailureReports(List<OutboxFailureReport> reports, Instant createdAt) {
        for (var report : reports) {
            var payload = writePayload(report);
            jdbcClient
                    .sql("""
                        insert into notification_outbox (event_type, payload, created_at)
                        select :eventType, :payload, :createdAt
                        where not exists (
                            select 1
                            from notification_outbox
                            where event_type = :eventType
                              and payload = :payload
                              and processed_at is null
                        )
                        """)
                    .param("eventType", PROCESSING_FAILURE_REPORT_EVENT_TYPE)
                    .param("payload", payload)
                    .param("createdAt", Timestamp.from(createdAt))
                    .update();
        }
    }

    @Override
    public List<OutboxNotification> claimNextLinkUpdatesToPublish(
            String processingOwner, Instant claimedAt, Instant processingUntil, int limit) {
        return claimNextToPublish(
                LINK_UPDATE_EVENT_TYPE,
                processingOwner,
                claimedAt,
                processingUntil,
                limit,
                outboxNotificationRowMapper);
    }

    @Override
    public List<OutboxFailureReport> claimNextProcessingFailureReportsToPublish(
            String processingOwner, Instant claimedAt, Instant processingUntil, int limit) {
        return claimNextToPublish(
                PROCESSING_FAILURE_REPORT_EVENT_TYPE,
                processingOwner,
                claimedAt,
                processingUntil,
                limit,
                outboxFailureReportRowMapper);
    }

    @Override
    public void markProcessed(long id, String processingOwner, Instant processedAt) {
        jdbcClient
                .sql("""
                    update notification_outbox
                    set processed_at = :processedAt,
                        processing_owner = null,
                        processing_until = null
                    where id = :id and processing_owner = :processingOwner
                    """)
                .param("id", id)
                .param("processingOwner", processingOwner)
                .param("processedAt", Timestamp.from(processedAt))
                .update();
    }

    @Override
    public void release(long id, String processingOwner) {
        jdbcClient
                .sql("""
                    update notification_outbox
                    set processing_owner = null,
                        processing_until = null
                    where id = :id and processing_owner = :processingOwner
                    """)
                .param("id", id)
                .param("processingOwner", processingOwner)
                .update();
    }

    private String writePayload(OutboxNotification notification) {
        try {
            return objectMapper.writeValueAsString(new LinkUpdateOutboxPayload(
                    notification.trackedLinkId(),
                    notification.trackedLinkUrl().toString(),
                    notification.description(),
                    notification.chatIds()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize notification outbox payload", exception);
        }
    }

    private String writePayload(OutboxFailureReport report) {
        try {
            return objectMapper.writeValueAsString(
                    new ProcessingFailureReportOutboxPayload(report.description(), report.chatIds()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize failure report outbox payload", exception);
        }
    }

    private <T> List<T> claimNextToPublish(
            String eventType,
            String processingOwner,
            Instant claimedAt,
            Instant processingUntil,
            int limit,
            RowMapper<T> rowMapper) {
        return jdbcClient
                .sql("""
                    with claimed as (
                        select id
                        from notification_outbox
                        where event_type = :eventType
                          and processed_at is null
                          and (processing_until is null or processing_until <= :claimedAt)
                        order by id
                        for update skip locked
                        limit :limit
                    )
                    update notification_outbox
                    set processing_owner = :processingOwner,
                        processing_until = :processingUntil
                    from claimed
                    where notification_outbox.id = claimed.id
                    returning notification_outbox.id,
                              notification_outbox.payload
                    """)
                .param("eventType", eventType)
                .param("claimedAt", Timestamp.from(claimedAt))
                .param("processingOwner", processingOwner)
                .param("processingUntil", Timestamp.from(processingUntil))
                .param("limit", limit)
                .query(rowMapper)
                .list();
    }

    private OutboxNotification mapOutboxNotification(ResultSet resultSet) throws SQLException {
        var payload = readPayload(resultSet.getString("payload"));
        return new OutboxNotification(
                resultSet.getLong("id"),
                payload.id(),
                URI.create(payload.url()),
                payload.description(),
                payload.tgChatIds());
    }

    private OutboxFailureReport mapOutboxFailureReport(ResultSet resultSet) throws SQLException {
        var payload = readFailureReportPayload(resultSet.getString("payload"));
        return new OutboxFailureReport(resultSet.getLong("id"), payload.description(), payload.tgChatIds());
    }

    private LinkUpdateOutboxPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, LinkUpdateOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize notification outbox payload", exception);
        }
    }

    private ProcessingFailureReportOutboxPayload readFailureReportPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ProcessingFailureReportOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize failure report outbox payload", exception);
        }
    }

    private record LinkUpdateOutboxPayload(long id, String url, String description, List<Long> tgChatIds) {}

    private record ProcessingFailureReportOutboxPayload(String description, List<Long> tgChatIds) {}
}
