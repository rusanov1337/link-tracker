package backend.academy.linktracker.scrapper.repository.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryChatRepositoryTest {

    private final InMemoryChatRepository repository = new InMemoryChatRepository();

    @Test
    void addIsIdempotentAndExistsReflectsState() {
        assertTrue(repository.add(101L));
        assertFalse(repository.add(101L));
        assertTrue(repository.exists(101L));
    }

    @Test
    void removeUpdatesState() {
        repository.add(202L);

        assertTrue(repository.remove(202L));
        assertFalse(repository.remove(202L));
        assertFalse(repository.exists(202L));
    }
}
