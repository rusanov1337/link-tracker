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
public class SubscriptionTagId implements Serializable {

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(nullable = false)
    private String tag;

    public SubscriptionTagId(Long chatId, Long linkId, String tag) {
        this.chatId = chatId;
        this.linkId = linkId;
        this.tag = tag;
    }
}
