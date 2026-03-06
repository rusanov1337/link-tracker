package backend.academy.linktracker.scrapper.repository.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryLinkSubscriptionRepositoryTest {

    private final InMemoryLinkSubscriptionRepository repository = new InMemoryLinkSubscriptionRepository();

    @Test
    void addNormalizesTagsAndIgnoresDuplicatePair() {
        var subscription =
                new LinkSubscription(1L, 100L, List.of(" work ", "work", "", "docs"), List.of("f1", "f1", " "));

        assertTrue(repository.add(subscription));
        assertFalse(repository.add(subscription));
        assertEquals(1, repository.count());

        var stored = repository.findByChatId(1L).getFirst();
        assertEquals(List.of("work", "docs"), stored.tags());
        assertEquals(List.of("f1"), stored.filters());
    }

    @Test
    void removeDeletesExactSubscriptionPair() {
        repository.add(new LinkSubscription(1L, 100L, List.of("bug"), List.of()));
        repository.add(new LinkSubscription(1L, 101L, List.of("feature"), List.of()));

        assertTrue(repository.remove(1L, 100L));
        assertFalse(repository.remove(1L, 100L));
        assertTrue(repository.exists(1L, 101L));
        assertFalse(repository.exists(1L, 100L));
    }

    @Test
    void findByLinkReturnsAllSubscribers() {
        repository.add(new LinkSubscription(1L, 200L, List.of("a"), List.of()));
        repository.add(new LinkSubscription(2L, 200L, List.of("b"), List.of()));
        repository.add(new LinkSubscription(1L, 201L, List.of("c"), List.of()));

        var byLink = repository.findByLinkId(200L);

        assertEquals(2, byLink.size());
        assertEquals(1L, byLink.get(0).chatId());
        assertEquals(2L, byLink.get(1).chatId());
    }

    @Test
    void findReturnsExactSubscriptionByChatAndLink() {
        repository.add(new LinkSubscription(7L, 700L, List.of("tag"), List.of("filter")));

        var found = repository.find(7L, 700L);

        assertTrue(found.isPresent());
        assertEquals(7L, found.orElseThrow().chatId());
        assertEquals(700L, found.orElseThrow().linkId());
    }
}
