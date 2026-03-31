package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdatePollingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdatePollingService.class);

    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final List<ExternalLinkClient> externalLinkClients;
    private final BotUpdatesClient botUpdatesClient;
    private final LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter;
    private final SchedulerProperties schedulerProperties;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            BotUpdatesClient botUpdatesClient,
            LinkUpdateDescriptionFormatter linkUpdateDescriptionFormatter,
            SchedulerProperties schedulerProperties) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.botUpdatesClient = botUpdatesClient;
        this.linkUpdateDescriptionFormatter = linkUpdateDescriptionFormatter;
        this.schedulerProperties = schedulerProperties;
    }

    public void checkUpdates() {
        var checkedAt = Instant.now();
        var checkedLinksCount = 0;
        var failedLinksByChat = new ConcurrentHashMap<Long, Set<URI>>();
        long afterId = 0;
        var executorService = createBatchExecutor();
        try {
            while (true) {
                var links =
                        trackedLinkRepository.findPageToCheck(checkedAt, afterId, schedulerProperties.getBatchSize());
                if (links.isEmpty()) {
                    break;
                }

                processBatch(links, checkedAt, failedLinksByChat, executorService);
                checkedLinksCount += links.size();
                afterId = links.getLast().id();
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

    private void processBatch(
            List<TrackedLink> links,
            Instant checkedAt,
            Map<Long, Set<URI>> failedLinksByChat,
            ExecutorService executorService) {
        if (executorService == null || links.size() <= 1) {
            links.forEach(link -> checkSingleLink(link, checkedAt, failedLinksByChat));
            return;
        }

        var chunkSize = Math.max(1, (int) Math.ceil((double) links.size() / schedulerProperties.getParallelism()));
        var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
        for (int start = 0; start < links.size(); start += chunkSize) {
            var end = Math.min(start + chunkSize, links.size());
            var chunk = List.copyOf(links.subList(start, end));
            tasks.add(() -> {
                chunk.forEach(link -> checkSingleLink(link, checkedAt, failedLinksByChat));
                return null;
            });
        }

        try {
            for (var future : executorService.invokeAll(tasks)) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Link check batch processing was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Link check batch processing failed", exception);
        }
    }

    private void checkSingleLink(TrackedLink trackedLink, Instant checkedAt, Map<Long, Set<URI>> failedLinksByChat) {
        try {
            var client = findClient(trackedLink);
            if (client.isEmpty()) {
                trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
                LOGGER.atDebug()
                        .addKeyValue("linkId", trackedLink.id())
                        .addKeyValue("url", trackedLink.url())
                        .log("No external client supports URL");
                return;
            }

            var checkResult = client.orElseThrow().fetchUpdates(trackedLink);
            if (!processFetchedState(trackedLink, checkedAt, checkResult)) {
                recordFailedLink(trackedLink, failedLinksByChat);
            }
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "checkSingleLink")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("Link check failed");
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            recordFailedLink(trackedLink, failedLinksByChat);
        }
    }

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }

    private boolean processFetchedState(TrackedLink trackedLink, Instant checkedAt, LinkCheckResult checkResult) {
        var currentState = trackedLink.withLastCheckedAt(checkedAt);
        if (checkResult.failed()) {
            trackedLinkRepository.update(currentState);
            return false;
        }
        if (checkResult.updates().isEmpty()) {
            trackedLinkRepository.update(currentState);
            return true;
        }

        var chatIds = linkSubscriptionRepository.findByLinkId(trackedLink.id()).stream()
                .map(subscription -> subscription.chatId())
                .toList();
        for (var update : checkResult.updates()) {
            if (!chatIds.isEmpty() && !notifyBot(trackedLink, update, chatIds)) {
                trackedLinkRepository.update(currentState);
                return false;
            }

            currentState = currentState
                    .withLastUpdatedAt(update.createdAt())
                    .withLastEventState(update.createdAt(), update.cursor());
        }

        trackedLinkRepository.update(currentState);
        return true;
    }

    private boolean notifyBot(TrackedLink trackedLink, DetectedUpdate update, List<Long> chatIds) {
        var description = linkUpdateDescriptionFormatter.format(update);
        try {
            botUpdatesClient.sendLinkUpdate(trackedLink.id(), trackedLink.url(), description, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("eventType", update.eventType())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
    }

    private void recordFailedLink(TrackedLink trackedLink, Map<Long, Set<URI>> failedLinksByChat) {
        for (var subscription : linkSubscriptionRepository.findByLinkId(trackedLink.id())) {
            failedLinksByChat
                    .computeIfAbsent(subscription.chatId(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(trackedLink.url());
        }
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
}
