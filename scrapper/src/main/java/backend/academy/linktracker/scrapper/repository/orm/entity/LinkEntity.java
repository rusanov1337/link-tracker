package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "links")
@Getter
@Setter
@NoArgsConstructor
public class LinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "last_event_cursor")
    private String lastEventCursor;

    public LinkEntity(
            Long id,
            String url,
            Instant createdAt,
            Instant lastCheckedAt,
            Instant lastUpdatedAt,
            Instant lastEventAt,
            String lastEventCursor) {
        this.id = id;
        this.url = url;
        this.createdAt = createdAt;
        this.lastCheckedAt = lastCheckedAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.lastEventAt = lastEventAt;
        this.lastEventCursor = lastEventCursor;
    }
}
