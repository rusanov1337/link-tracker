package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionEntity {

    @EmbeddedId
    private SubscriptionId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SubscriptionEntity(SubscriptionId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }
}
