package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_filters")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionFilterEntity {

    @EmbeddedId
    private SubscriptionFilterId id;

    public SubscriptionFilterEntity(SubscriptionFilterId id) {
        this.id = id;
    }
}
