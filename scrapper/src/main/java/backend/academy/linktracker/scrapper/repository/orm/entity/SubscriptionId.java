package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
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
public class SubscriptionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    public SubscriptionId(Long chatId, Long linkId) {
        this.chatId = chatId;
        this.linkId = linkId;
    }
}
