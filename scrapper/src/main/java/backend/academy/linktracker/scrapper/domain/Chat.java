package backend.academy.linktracker.scrapper.domain;

import java.time.Instant;

public record Chat(long id, Instant registeredAt) {

    public static Chat register(long id, Instant now) {
        return new Chat(id, now);
    }
}
