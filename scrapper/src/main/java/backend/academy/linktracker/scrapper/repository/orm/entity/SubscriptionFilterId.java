package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class SubscriptionFilterId implements Serializable {

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "filter_value", nullable = false)
    private String filterValue;

    public SubscriptionFilterId(Long chatId, Long linkId, String filterValue) {
        this.chatId = chatId;
        this.linkId = linkId;
        this.filterValue = filterValue;
    }
}
