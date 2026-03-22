package backend.academy.linktracker.scrapper.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.repository.orm.OrmChatRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlChatRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlTrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class RepositoryIntegrationTestSupport {

    @Autowired
    protected ChatRepository chatRepository;

    @Autowired
    protected TrackedLinkRepository trackedLinkRepository;

    @Autowired
    protected LinkSubscriptionRepository linkSubscriptionRepository;

    @Test
    void usesConfiguredRepositoryImplementation() {
        assertInstanceOf(expectedChatRepositoryType(), chatRepository);
        assertInstanceOf(expectedTrackedLinkRepositoryType(), trackedLinkRepository);
        assertInstanceOf(expectedLinkSubscriptionRepositoryType(), linkSubscriptionRepository);
    }

    @Test
    void chatCrudWorks() {
        assertTrue(chatRepository.add(1L));
        assertFalse(chatRepository.add(1L));
        assertTrue(chatRepository.exists(1L));
        assertEquals(List.of(1L), chatRepository.findAllIds().stream().sorted().toList());

        assertTrue(chatRepository.remove(1L));
        assertFalse(chatRepository.exists(1L));
        assertFalse(chatRepository.remove(1L));
    }

    @Test
    void trackedLinkCrudPreservesCanonicalization() {
        var now = Instant.parse("2026-03-22T10:00:00Z");

        var created = trackedLinkRepository.create(URI.create("https://github.com/Org/Repo/"), now);
        var duplicate = trackedLinkRepository.create(
                URI.create("HTTPS://GITHUB.COM/org/repo?tab=readme#top"), now.plusSeconds(5));

        assertEquals(created.id(), duplicate.id());
        assertEquals("https://github.com/org/repo", created.url().toString());
        assertEquals(1, trackedLinkRepository.count());
        assertTrue(trackedLinkRepository
                .findByUrl(URI.create("https://github.com/org/repo"))
                .isPresent());

        var updated = created.withLastCheckedAt(now.plusSeconds(15)).withLastUpdatedAt(now.plusSeconds(30));
        trackedLinkRepository.update(updated);

        var stored = trackedLinkRepository.findById(created.id()).orElseThrow();
        assertEquals(updated.lastCheckedAt(), stored.lastCheckedAt());
        assertEquals(updated.lastUpdatedAt(), stored.lastUpdatedAt());

        assertTrue(trackedLinkRepository.delete(created.id()));
        assertFalse(trackedLinkRepository.delete(created.id()));
        assertEquals(0, trackedLinkRepository.count());
    }

    @Test
    void subscriptionCrudPersistsTagsAndFilters() {
        assertTrue(chatRepository.add(1L));
        var trackedLink = trackedLinkRepository.create(
                URI.create("https://stackoverflow.com/questions/123/example"), Instant.now());
        var subscription = new LinkSubscription(
                1L,
                trackedLink.id(),
                List.of("work", " bug ", "work", ""),
                List.of("author=me", "label=java", "author=me"));

        assertTrue(linkSubscriptionRepository.add(subscription));
        assertFalse(linkSubscriptionRepository.add(subscription));
        assertTrue(linkSubscriptionRepository.exists(1L, trackedLink.id()));

        var stored = linkSubscriptionRepository.find(1L, trackedLink.id()).orElseThrow();
        assertEquals(List.of("bug", "work"), stored.tags());
        assertEquals(List.of("author=me", "label=java"), stored.filters());
        assertEquals(1, linkSubscriptionRepository.findByChatId(1L).size());
        assertEquals(
                1, linkSubscriptionRepository.findByLinkId(trackedLink.id()).size());
        assertEquals(1, linkSubscriptionRepository.count());

        assertTrue(linkSubscriptionRepository.remove(1L, trackedLink.id()));
        assertFalse(linkSubscriptionRepository.exists(1L, trackedLink.id()));
        assertFalse(linkSubscriptionRepository.remove(1L, trackedLink.id()));
    }

    protected abstract Class<? extends ChatRepository> expectedChatRepositoryType();

    protected abstract Class<? extends TrackedLinkRepository> expectedTrackedLinkRepositoryType();

    protected abstract Class<? extends LinkSubscriptionRepository> expectedLinkSubscriptionRepositoryType();

    protected final Class<? extends ChatRepository> sqlChatRepository() {
        return SqlChatRepository.class;
    }

    protected final Class<? extends TrackedLinkRepository> sqlTrackedLinkRepository() {
        return SqlTrackedLinkRepository.class;
    }

    protected final Class<? extends LinkSubscriptionRepository> sqlLinkSubscriptionRepository() {
        return SqlLinkSubscriptionRepository.class;
    }

    protected final Class<? extends ChatRepository> ormChatRepository() {
        return OrmChatRepository.class;
    }

    protected final Class<? extends TrackedLinkRepository> ormTrackedLinkRepository() {
        return OrmTrackedLinkRepository.class;
    }

    protected final Class<? extends LinkSubscriptionRepository> ormLinkSubscriptionRepository() {
        return OrmLinkSubscriptionRepository.class;
    }
}
