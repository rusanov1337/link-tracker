package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.DatabaseProperties;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private final List<ExternalLinkClient> externalLinkClients;
    private final BotUpdatesClient botUpdatesClient;
    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter;
    private final SchedulerProperties schedulerProperties;
    private final DatabaseProperties databaseProperties;
    private final TransactionTemplate transactionTemplate;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            BotUpdatesClient botUpdatesClient,
            LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter,
            SchedulerProperties schedulerProperties,
            DatabaseProperties databaseProperties,
            PlatformTransactionManager transactionManager) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.botUpdatesClient = botUpdatesClient;
        this.linkUpdateDescriptionFormatter = linkUpdateDescriptionFormatter;
        this.schedulerProperties = schedulerProperties;
        this.databaseProperties = databaseProperties;
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
                sendNotifications(batchOutcome.notifications(), failedLinksByChat);
            }
        } finally {
            shutdownExecutor(executorService);
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "checkUpdates")
                .addKeyValue("linksChecked", checkedLinksCount)
                .log("Links check finished");
        sendFailureReports(failedLinksByChat);
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
        return transactionTemplate.execute(status -> {
            // Keep row locks until all fetched states are applied to avoid duplicate notifications
            // when several scrapper instances poll the same rows concurrently.
            var links = trackedLinkRepository.lockNextPageToCheck(checkedAt, schedulerProperties.getBatchSize());
            if (links.isEmpty()) {
                return BatchOutcome.empty();
            }

            var notifications = new ArrayList<PendingNotification>();
            var failedLinksByChat = new HashMap<Long, Set<URI>>();
            for (var fetchedState : fetchBatchStates(links, executorService)) {
                applyFetchedState(fetchedState, checkedAt, notifications, failedLinksByChat);
            }
            return new BatchOutcome(links.size(), List.copyOf(notifications), copyFailureLinks(failedLinksByChat));
        });
    }

    private List<FetchedLinkState> fetchBatchStates(List<TrackedLink> links, ExecutorService executorService) {
        if (executorService == null || links.size() <= 1) {
            return links.stream().map(this::fetchLinkState).toList();
        }

        var chunkSize = Math.max(1, (int) Math.ceil((double) links.size() / schedulerProperties.getParallelism()));
        var tasks = new ArrayList<Callable<List<FetchedLinkState>>>();
        for (int start = 0; start < links.size(); start += chunkSize) {
            var end = Math.min(start + chunkSize, links.size());
            var chunk = List.copyOf(links.subList(start, end));
            tasks.add(() -> chunk.stream().map(this::fetchLinkState).toList());
        }

        try {
            var fetchedStates = new ArrayList<FetchedLinkState>(links.size());
            for (var future : executorService.invokeAll(tasks)) {
                fetchedStates.addAll(future.get());
            }
            return List.copyOf(fetchedStates);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Link check batch processing was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Link check batch processing failed", exception);
        }
    }

    private FetchedLinkState fetchLinkState(TrackedLink trackedLink) {
        try {
            var client = findClient(trackedLink);
            if (client.isEmpty()) {
                LOGGER.atDebug()
                        .addKeyValue("linkId", trackedLink.id())
                        .addKeyValue("url", trackedLink.url())
                        .log("No external client supports URL");
                return FetchedLinkState.noSupportedClient(trackedLink);
            }

            return FetchedLinkState.success(trackedLink, client.orElseThrow().fetchUpdates(trackedLink));
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "checkSingleLink")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("Link check failed");
            return FetchedLinkState.failure(trackedLink, exception);
        }
    }

    private void applyFetchedState(
            FetchedLinkState fetchedState,
            Instant checkedAt,
            List<PendingNotification> notifications,
            Map<Long, Set<URI>> failedLinksByChat) {
        var trackedLink = fetchedState.trackedLink();
        if (!fetchedState.hasSupportedClient()) {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            return;
        }
        if (fetchedState.failure() != null) {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            recordFailedLink(trackedLink, failedLinksByChat);
            return;
        }
        processFetchedState(trackedLink, checkedAt, fetchedState.checkResult(), notifications, failedLinksByChat);
    }

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }

    private void processFetchedState(
            TrackedLink trackedLink,
            Instant checkedAt,
            LinkCheckResult checkResult,
            List<PendingNotification> notifications,
            Map<Long, Set<URI>> failedLinksByChat) {
        var currentState = trackedLink.withLastCheckedAt(checkedAt);
        if (checkResult.failed()) {
            trackedLinkRepository.update(currentState);
            recordFailedLink(trackedLink, failedLinksByChat);
            return;
        }
        if (checkResult.updates().isEmpty()) {
            trackedLinkRepository.update(currentState);
            return;
        }

        var chatIds = findSubscriberChatIds(trackedLink.id());
        for (var update : checkResult.updates()) {
            if (!chatIds.isEmpty()) {
                notifications.add(new PendingNotification(trackedLink.id(), trackedLink.url(), update, chatIds));
            }
            currentState = currentState
                    .withLastUpdatedAt(update.createdAt())
                    .withLastEventState(update.createdAt(), update.cursor());
        }

        trackedLinkRepository.update(currentState);
    }

    private void sendNotifications(List<PendingNotification> notifications, Map<Long, Set<URI>> failedLinksByChat) {
        for (var notification : notifications) {
            if (!notifyBot(
                    notification.trackedLinkId(),
                    notification.trackedLinkUrl(),
                    notification.update(),
                    notification.chatIds())) {
                recordFailedLink(notification.trackedLinkUrl(), notification.chatIds(), failedLinksByChat);
            }
        }
    }

    private boolean notifyBot(long trackedLinkId, URI trackedLinkUrl, DetectedUpdate update, List<Long> chatIds) {
        var description = linkUpdateDescriptionFormatter.format(update);
        try {
            botUpdatesClient.sendLinkUpdate(trackedLinkId, trackedLinkUrl, description, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLinkId)
                    .addKeyValue("url", trackedLinkUrl)
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }

    private void recordFailedLink(TrackedLink trackedLink, Map<Long, Set<URI>> failedLinksByChat) {
        for (var chatId : findSubscriberChatIds(trackedLink.id())) {
            failedLinksByChat
                    .computeIfAbsent(chatId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(trackedLink.url());
        }
    }

    private void recordFailedLink(URI trackedLinkUrl, List<Long> chatIds, Map<Long, Set<URI>> failedLinksByChat) {
        for (var chatId : chatIds) {
            failedLinksByChat
                    .computeIfAbsent(chatId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(trackedLinkUrl);
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

    private void sendFailureReports(Map<Long, Set<URI>> failedLinksByChat) {
        for (var entry : failedLinksByChat.entrySet()) {
            var description = buildFailureReport(entry.getValue());
            try {
                botUpdatesClient.sendProcessingFailureReport(description, List.of(entry.getKey()));
            } catch (RuntimeException exception) {
                LOGGER.atWarn()
                        .addKeyValue("operation", "sendFailureReport")
                        .addKeyValue("chatId", entry.getKey())
                        .addKeyValue("failedLinksCount", entry.getValue().size())
                        .setCause(exception)
                        .log("Failed links report delivery failed");
            }
        }
    }

    private String buildFailureReport(Collection<URI> failedUrls) {
        var uniqueUrls = failedUrls.stream().map(URI::toString).sorted().toList();
        return "Не удалось обработать ссылки:" + System.lineSeparator()
                + uniqueUrls.stream()
                        .map(url -> "- " + url)
                        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private List<Long> findSubscriberChatIds(long linkId) {
        var chatIds = new ArrayList<Long>();
        var offset = 0;
        while (true) {
            var subscriptions =
                    linkSubscriptionRepository.findByLinkId(linkId, databaseProperties.getPageSize(), offset);
            if (subscriptions.isEmpty()) {
                return List.copyOf(chatIds);
            }

            subscriptions.stream().map(subscription -> subscription.chatId()).forEach(chatIds::add);
            offset += subscriptions.size();
        }
    }

    private record FetchedLinkState(
            TrackedLink trackedLink,
            boolean hasSupportedClient,
            LinkCheckResult checkResult,
            RuntimeException failure) {

        private static FetchedLinkState noSupportedClient(TrackedLink trackedLink) {
            return new FetchedLinkState(trackedLink, false, null, null);
        }

        private static FetchedLinkState success(TrackedLink trackedLink, LinkCheckResult checkResult) {
            return new FetchedLinkState(trackedLink, true, checkResult, null);
        }

        private static FetchedLinkState failure(TrackedLink trackedLink, RuntimeException failure) {
            return new FetchedLinkState(trackedLink, true, null, failure);
        }
    }

    private record PendingNotification(
            long trackedLinkId, URI trackedLinkUrl, DetectedUpdate update, List<Long> chatIds) {
        private PendingNotification {
            chatIds = List.copyOf(chatIds);
        }
    }

    private record BatchOutcome(
            int processedLinksCount, List<PendingNotification> notifications, Map<Long, Set<URI>> failedLinksByChat) {
        private static BatchOutcome empty() {
            return new BatchOutcome(0, List.of(), Map.of());
        }
    }
}
