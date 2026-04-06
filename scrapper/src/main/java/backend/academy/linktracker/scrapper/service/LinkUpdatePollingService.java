package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.bot.BotUpdatesClient;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.DatabaseProperties;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkUpdatePollingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdatePollingService.class);
    private static final String UPDATE_MESSAGE = "Обнаружено обновление отслеживаемой ссылки";

    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final List<ExternalLinkClient> externalLinkClients;
    private final BotUpdatesClient botUpdatesClient;
    private final SchedulerProperties schedulerProperties;
    private final DatabaseProperties databaseProperties;
    private final TransactionTemplate transactionTemplate;

    public LinkUpdatePollingService(
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            BotUpdatesClient botUpdatesClient,
            SchedulerProperties schedulerProperties,
            DatabaseProperties databaseProperties,
            PlatformTransactionManager transactionManager) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.botUpdatesClient = botUpdatesClient;
        this.schedulerProperties = schedulerProperties;
        this.databaseProperties = databaseProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void checkUpdates() {
        var checkedAt = Instant.now();
        var checkedLinksCount = 0;
        while (true) {
            var processedBatchSize = processNextBatch(checkedAt);
            if (processedBatchSize == 0) {
                break;
            }
            checkedLinksCount += processedBatchSize;
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "checkUpdates")
                .addKeyValue("linksChecked", checkedLinksCount)
                .log("Links check finished");
    }

    private int processNextBatch(Instant checkedAt) {
        return transactionTemplate.execute(status -> {
            // Keep row locks until the whole batch is processed to avoid duplicate notifications
            // when multiple scrapper instances poll the same links concurrently.
            var links = trackedLinkRepository.lockNextPageToCheck(checkedAt, schedulerProperties.getBatchSize());
            if (links.isEmpty()) {
                return 0;
            }

            for (var trackedLink : links) {
                checkSingleLink(trackedLink, checkedAt);
            }
            return links.size();
        });
    }

    private void checkSingleLink(TrackedLink trackedLink, Instant checkedAt) {
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

            var lastUpdated = client.orElseThrow().fetchLastUpdated(trackedLink.url());
            processFetchedState(trackedLink, checkedAt, lastUpdated);
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "checkSingleLink")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("Link check failed");
        }
    }

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }

    private void processFetchedState(TrackedLink trackedLink, Instant checkedAt, Optional<Instant> lastUpdated) {
        if (lastUpdated.isEmpty() || !lastUpdated.orElseThrow().isAfter(trackedLink.lastUpdatedAt())) {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
            return;
        }

        var chatIds = findSubscriberChatIds(trackedLink.id());
        if (chatIds.isEmpty()) {
            trackedLinkRepository.update(
                    trackedLink.withLastCheckedAt(checkedAt).withLastUpdatedAt(lastUpdated.orElseThrow()));
            return;
        }

        if (notifyBot(trackedLink, chatIds)) {
            trackedLinkRepository.update(
                    trackedLink.withLastCheckedAt(checkedAt).withLastUpdatedAt(lastUpdated.orElseThrow()));
        } else {
            trackedLinkRepository.update(trackedLink.withLastCheckedAt(checkedAt));
        }
    }

    private boolean notifyBot(TrackedLink trackedLink, List<Long> chatIds) {
        try {
            botUpdatesClient.sendLinkUpdate(trackedLink.id(), trackedLink.url(), UPDATE_MESSAGE, chatIds);
            LOGGER.atInfo()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", true)
                    .log("Update notification sent to bot");
            return true;
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "notifyBot")
                    .addKeyValue("linkId", trackedLink.id())
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("chatIdsCount", chatIds.size())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Bot update notification failed");
            return false;
        }
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
}
