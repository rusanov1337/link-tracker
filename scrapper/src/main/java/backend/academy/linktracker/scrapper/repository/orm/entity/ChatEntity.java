package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
public class ChatEntity {

    @Id
    private Long id;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public ChatEntity(Long id, Instant registeredAt) {
        this.id = id;
        this.registeredAt = registeredAt;
    }
}
