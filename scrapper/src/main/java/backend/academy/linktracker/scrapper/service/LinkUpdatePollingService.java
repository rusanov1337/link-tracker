package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkUpdatePollingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdatePollingService.class);

    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final LinkUpdateFetchService linkUpdateFetchService;
    private final LinkUpdateNotificationDispatcher notificationDispatcher;
    private final SchedulerProperties schedulerProperties;
    private final TransactionTemplate transactionTemplate;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            LinkUpdateFetchService linkUpdateFetchService,
            LinkUpdateNotificationDispatcher notificationDispatcher,
            SchedulerProperties schedulerProperties,
            PlatformTransactionManager transactionManager) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.linkUpdateFetchService = linkUpdateFetchService;
        this.notificationDispatcher = notificationDispatcher;
        this.schedulerProperties = schedulerProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void checkUpdates() {
        var checkedAt = Instant.now();
        var checkedLinksCount = 0;
        var failedLinksByChat = new ConcurrentHashMap<Long, Set<URI>>();
        var executorService = createBatchExecutor();
        try {
            while (true) {
                var batchOutcome = processNextBatch(checkedAt, executorService);
                if (batchOutcome.processedLinksCount() == 0) {
                    break;
                }
                checkedLinksCount += batchOutcome.processedLinksCount();
                mergeFailedLinks(batchOutcome.failedLinksByChat(), failedLinksByChat);
                notificationDispatcher.sendNotifications(batchOutcome.notifications(), failedLinksByChat);
            }
        } finally {
            shutdownExecutor(executorService);
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "checkUpdates")
                .addKeyValue("linksChecked", checkedLinksCount)
                .log("Links check finished");
        notificationDispatcher.sendFailureReports(failedLinksByChat);
    }

    private ExecutorService createBatchExecutor() {
        if (schedulerProperties.getParallelism() <= 1) {
            return null;
        }

        return Executors.newFixedThreadPool(schedulerProperties.getParallelism());
    }

    private void shutdownExecutor(ExecutorService executorService) {
        if (executorService == null) {
            return;
        }

        executorService.shutdown();
    }

    private BatchOutcome processNextBatch(Instant checkedAt, ExecutorService executorService) {
        var processingOwner = UUID.randomUUID().toString();
        var claimedAt = Instant.now();
        var processingUntil = claimedAt.plus(schedulerProperties.getProcessingLease());
        var links = transactionTemplate.execute(status -> trackedLinkRepository.claimNextPageToCheck(
                checkedAt, processingOwner, claimedAt, processingUntil, schedulerProperties.getBatchSize()));
        if (links.isEmpty()) {
            return BatchOutcome.empty();
        }

        var fetchedStates = linkUpdateFetchService.fetchBatchStates(links, executorService);

        return transactionTemplate.execute(status -> {
            var subscriberChatIdsByLinkId = linkSubscriptionRepository.findChatIdsByLinkIds(
                    links.stream().map(TrackedLink::id).toList());
            var notifications = new ArrayList<PendingNotification>();
            var failedLinksByChat = new HashMap<Long, Set<URI>>();
            for (var fetchedState : fetchedStates) {
                applyFetchedState(
                        fetchedState,
                        checkedAt,
                        processingOwner,
                        notifications,
                        failedLinksByChat,
                        subscriberChatIdsByLinkId);
            }
            return new BatchOutcome(links.size(), List.copyOf(notifications), copyFailureLinks(failedLinksByChat));
        });
    }

    private void applyFetchedState(
            FetchedLinkState fetchedState,
            Instant checkedAt,
            String processingOwner,
            List<PendingNotification> notifications,
            Map<Long, Set<URI>> failedLinksByChat,
            Map<Long, List<Long>> subscriberChatIdsByLinkId) {
        var trackedLink = fetchedState.trackedLink();
        if (!fetchedState.hasSupportedClient()) {
            applyStateIfLeaseOwned(trackedLink.withLastCheckedAt(checkedAt), processingOwner);
            return;
        }
        if (fetchedState.failure() != null) {
            if (applyStateIfLeaseOwned(trackedLink.withLastCheckedAt(checkedAt), processingOwner)) {
                recordFailedLink(trackedLink, subscriberChatIdsByLinkId, failedLinksByChat);
            }
            return;
        }
        processFetchedState(
                trackedLink,
                checkedAt,
                processingOwner,
                fetchedState.checkResult(),
                notifications,
                failedLinksByChat,
                subscriberChatIdsByLinkId);
    }

    private void processFetchedState(
            TrackedLink trackedLink,
            Instant checkedAt,
            String processingOwner,
            LinkCheckResult checkResult,
            List<PendingNotification> notifications,
            Map<Long, Set<URI>> failedLinksByChat,
            Map<Long, List<Long>> subscriberChatIdsByLinkId) {
        var currentState = trackedLink.withLastCheckedAt(checkedAt);
        if (checkResult.failed()) {
            if (applyStateIfLeaseOwned(currentState, processingOwner)) {
                recordFailedLink(trackedLink, subscriberChatIdsByLinkId, failedLinksByChat);
            }
            return;
        }
        if (checkResult.updates().isEmpty()) {
            applyStateIfLeaseOwned(currentState, processingOwner);
            return;
        }

        var chatIds = subscriberChatIdsByLinkId.getOrDefault(trackedLink.id(), List.of());
        for (var update : checkResult.updates()) {
            currentState = currentState
                    .withLastUpdatedAt(update.createdAt())
                    .withLastEventState(update.createdAt(), update.cursor());
        }

        if (applyStateIfLeaseOwned(currentState, processingOwner) && !chatIds.isEmpty()) {
            for (var update : checkResult.updates()) {
                notifications.add(new PendingNotification(trackedLink.id(), trackedLink.url(), update, chatIds));
            }
        }
    }

    private boolean applyStateIfLeaseOwned(TrackedLink trackedLink, String processingOwner) {
        var applied = trackedLinkRepository.updateIfProcessingOwner(trackedLink, processingOwner);
        if (!applied) {
            LOGGER.atDebug()
                    .addKeyValue("operation", "applyLinkState")
                    .addKeyValue("linkId", trackedLink.id())
                    .log("Skipped applying link state because processing lease is no longer owned");
        }
        return applied;
    }

    private void recordFailedLink(
            TrackedLink trackedLink,
            Map<Long, List<Long>> subscriberChatIdsByLinkId,
            Map<Long, Set<URI>> failedLinksByChat) {
        for (var chatId : subscriberChatIdsByLinkId.getOrDefault(trackedLink.id(), List.of())) {
            failedLinksByChat
                    .computeIfAbsent(chatId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(trackedLink.url());
        }
    }

    private void mergeFailedLinks(Map<Long, Set<URI>> source, Map<Long, Set<URI>> target) {
        for (var entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), ignored -> ConcurrentHashMap.newKeySet())
                    .addAll(entry.getValue());
        }
    }

    private Map<Long, Set<URI>> copyFailureLinks(Map<Long, Set<URI>> failedLinksByChat) {
        var copy = new HashMap<Long, Set<URI>>();
        for (var entry : failedLinksByChat.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private record BatchOutcome(
            int processedLinksCount, List<PendingNotification> notifications, Map<Long, Set<URI>> failedLinksByChat) {
        private static BatchOutcome empty() {
            return new BatchOutcome(0, List.of(), Map.of());
        }
    }
}
