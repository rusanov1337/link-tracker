package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdateFetchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdateFetchService.class);

    private final List<ExternalLinkClient> externalLinkClients;
    private final SchedulerProperties schedulerProperties;

    public LinkUpdateFetchService(
            List<ExternalLinkClient> externalLinkClients, SchedulerProperties schedulerProperties) {
        this.externalLinkClients = externalLinkClients;
        this.schedulerProperties = schedulerProperties;
    }

    List<FetchedLinkState> fetchBatchStates(List<TrackedLink> links, ExecutorService executorService) {
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

    private Optional<ExternalLinkClient> findClient(TrackedLink trackedLink) {
        return externalLinkClients.stream()
                .filter(client -> client.supports(trackedLink.url()))
                .findFirst();
    }
}
