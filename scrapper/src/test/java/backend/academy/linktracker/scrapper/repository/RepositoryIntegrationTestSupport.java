package backend.academy.linktracker.scrapper.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.orm.OrmChatRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmSubscriptionTagRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlChatRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlLinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlSubscriptionTagRepository;
import backend.academy.linktracker.scrapper.repository.sql.SqlTrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class RepositoryIntegrationTestSupport extends DatabaseCleanupSupport {

    @Autowired
    protected ChatRepository chatRepository;

    @Autowired
    protected TrackedLinkRepository trackedLinkRepository;

    @Autowired
    protected LinkSubscriptionRepository linkSubscriptionRepository;

    @Autowired
    protected SubscriptionTagRepository subscriptionTagRepository;

    @Test
    void usesConfiguredRepositoryImplementation() {
        assertInstanceOf(expectedChatRepositoryType(), chatRepository);
        assertInstanceOf(expectedTrackedLinkRepositoryType(), trackedLinkRepository);
        assertInstanceOf(expectedLinkSubscriptionRepositoryType(), linkSubscriptionRepository);
        assertInstanceOf(expectedSubscriptionTagRepositoryType(), subscriptionTagRepository);
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
    void trackedLinkFindByIdsReturnsOrderedMatches() {
        var first = trackedLinkRepository.create(
                URI.create("https://github.com/org/one"), Instant.parse("2026-03-22T10:00:00Z"));
        var second = trackedLinkRepository.create(
                URI.create("https://github.com/org/two"), Instant.parse("2026-03-22T10:00:01Z"));
        trackedLinkRepository.create(URI.create("https://github.com/org/three"), Instant.parse("2026-03-22T10:00:02Z"));

        var found = trackedLinkRepository.findByIds(List.of(second.id(), 999L, first.id()));

        assertEquals(
                List.of(first.id(), second.id()),
                found.stream().map(TrackedLink::id).toList());
    }

    @Test
    void trackedLinkFindAllSupportsPagination() {
        trackedLinkRepository.create(URI.create("https://github.com/org/one"), Instant.parse("2026-03-22T10:00:00Z"));
        var second = trackedLinkRepository.create(
                URI.create("https://github.com/org/two"), Instant.parse("2026-03-22T10:00:01Z"));
        var third = trackedLinkRepository.create(
                URI.create("https://github.com/org/three"), Instant.parse("2026-03-22T10:00:02Z"));

        var page = trackedLinkRepository.findAll(2, 1);

        assertEquals(
                List.of(second.id(), third.id()),
                page.stream().map(TrackedLink::id).toList());
    }

    @Test
    void trackedLinkPageToCheckReturnsStableBatch() {
        var first = trackedLinkRepository.create(
                URI.create("https://github.com/org/one"), Instant.parse("2026-03-22T10:00:00Z"));
        var second = trackedLinkRepository.create(
                URI.create("https://github.com/org/two"), Instant.parse("2026-03-22T10:00:01Z"));
        var third = trackedLinkRepository.create(
                URI.create("https://github.com/org/three"), Instant.parse("2026-03-22T10:00:02Z"));
        var skipped = trackedLinkRepository.create(
                URI.create("https://github.com/org/four"), Instant.parse("2026-03-22T10:00:03Z"));
        trackedLinkRepository.update(skipped.withLastCheckedAt(Instant.parse("2026-03-22T10:01:00Z")));

        var firstPage = trackedLinkRepository.findPageToCheck(Instant.parse("2026-03-22T10:00:30Z"), 0, 2);
        var secondPage = trackedLinkRepository.findPageToCheck(
                Instant.parse("2026-03-22T10:00:30Z"), firstPage.getLast().id(), 2);

        assertEquals(
                List.of(first.id(), second.id()),
                firstPage.stream().map(TrackedLink::id).toList());
        assertEquals(
                List.of(third.id()), secondPage.stream().map(TrackedLink::id).toList());
    }

    @Test
    void trackedLinkLockNextPageToCheckReturnsEligibleBatch() {
        var first = trackedLinkRepository.create(
                URI.create("https://github.com/org/one"), Instant.parse("2026-03-22T10:00:00Z"));
        var second = trackedLinkRepository.create(
                URI.create("https://github.com/org/two"), Instant.parse("2026-03-22T10:00:01Z"));
        trackedLinkRepository.create(URI.create("https://github.com/org/three"), Instant.parse("2026-03-22T10:01:00Z"));

        var locked = trackedLinkRepository.lockNextPageToCheck(Instant.parse("2026-03-22T10:00:30Z"), 2);

        assertEquals(
                List.of(first.id(), second.id()),
                locked.stream().map(TrackedLink::id).toList());
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

    @Test
    void subscriptionQueriesSupportPagination() {
        assertTrue(chatRepository.add(1L));
        assertTrue(chatRepository.add(2L));
        assertTrue(chatRepository.add(3L));
        var firstLink = trackedLinkRepository.create(URI.create("https://github.com/org/one"), Instant.now());
        var secondLink = trackedLinkRepository.create(URI.create("https://github.com/org/two"), Instant.now());

        assertTrue(linkSubscriptionRepository.add(new LinkSubscription(1L, firstLink.id(), List.of("a"), List.of())));
        assertTrue(linkSubscriptionRepository.add(new LinkSubscription(1L, secondLink.id(), List.of("b"), List.of())));
        assertTrue(linkSubscriptionRepository.add(new LinkSubscription(2L, firstLink.id(), List.of("c"), List.of())));
        assertTrue(linkSubscriptionRepository.add(new LinkSubscription(3L, firstLink.id(), List.of("d"), List.of())));

        var byChatPage = linkSubscriptionRepository.findByChatId(1L, 1, 1);
        var byLinkPage = linkSubscriptionRepository.findByLinkId(firstLink.id(), 2, 1);
        var allPage = linkSubscriptionRepository.findAll(2, 1);

        assertEquals(
                List.of(secondLink.id()),
                byChatPage.stream().map(LinkSubscription::linkId).toList());
        assertEquals(
                List.of(2L, 3L),
                byLinkPage.stream().map(LinkSubscription::chatId).toList());
        assertEquals(
                List.of(
                        new LinkSubscription(1L, secondLink.id(), List.of("b"), List.of()),
                        new LinkSubscription(2L, firstLink.id(), List.of("c"), List.of())),
                allPage);
    }

    @Test
    void subscriptionTagCrudWorksSeparately() {
        assertTrue(chatRepository.add(1L));
        var trackedLink = trackedLinkRepository.create(
                URI.create("https://github.com/org/repo"), Instant.parse("2026-03-22T10:00:00Z"));
        assertTrue(
                linkSubscriptionRepository.add(new LinkSubscription(1L, trackedLink.id(), List.of("work"), List.of())));

        assertEquals(List.of("work"), subscriptionTagRepository.findBySubscription(1L, trackedLink.id()));
        assertFalse(subscriptionTagRepository.add(1L, trackedLink.id() + 1, "ghost"));
        assertTrue(subscriptionTagRepository.add(1L, trackedLink.id(), " bug "));
        assertFalse(subscriptionTagRepository.add(1L, trackedLink.id(), "bug"));
        assertFalse(subscriptionTagRepository.add(1L, trackedLink.id(), "   "));
        assertEquals(List.of("bug", "work"), subscriptionTagRepository.findBySubscription(1L, trackedLink.id()));

        assertTrue(subscriptionTagRepository.update(1L, trackedLink.id(), "work", "docs"));
        assertEquals(List.of("bug", "docs"), subscriptionTagRepository.findBySubscription(1L, trackedLink.id()));

        assertTrue(subscriptionTagRepository.update(1L, trackedLink.id(), "docs", "bug"));
        assertEquals(List.of("bug"), subscriptionTagRepository.findBySubscription(1L, trackedLink.id()));
        assertTrue(subscriptionTagRepository.remove(1L, trackedLink.id(), "bug"));
        assertFalse(subscriptionTagRepository.remove(1L, trackedLink.id(), "bug"));
        assertEquals(List.of(), subscriptionTagRepository.findBySubscription(1L, trackedLink.id()));
        assertEquals(0, subscriptionTagRepository.count());
    }

    protected abstract Class<? extends ChatRepository> expectedChatRepositoryType();

    protected abstract Class<? extends TrackedLinkRepository> expectedTrackedLinkRepositoryType();

    protected abstract Class<? extends LinkSubscriptionRepository> expectedLinkSubscriptionRepositoryType();

    protected abstract Class<? extends SubscriptionTagRepository> expectedSubscriptionTagRepositoryType();

    protected final Class<? extends ChatRepository> sqlChatRepository() {
        return SqlChatRepository.class;
    }

    protected final Class<? extends TrackedLinkRepository> sqlTrackedLinkRepository() {
        return SqlTrackedLinkRepository.class;
    }

    protected final Class<? extends LinkSubscriptionRepository> sqlLinkSubscriptionRepository() {
        return SqlLinkSubscriptionRepository.class;
    }

    protected final Class<? extends SubscriptionTagRepository> sqlSubscriptionTagRepository() {
        return SqlSubscriptionTagRepository.class;
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

    protected final Class<? extends SubscriptionTagRepository> ormSubscriptionTagRepository() {
        return OrmSubscriptionTagRepository.class;
    }
}
