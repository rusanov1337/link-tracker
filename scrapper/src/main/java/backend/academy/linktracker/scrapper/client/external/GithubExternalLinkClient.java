package backend.academy.linktracker.scrapper.client.external;

import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.domain.UpdateProvider;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GithubExternalLinkClient implements ExternalLinkClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubExternalLinkClient.class);
    private static final String CLIENT_NAME = "github";

    private final RestClient restClient;
    private final GithubProperties githubProperties;
    private final HttpResilienceExecutor resilienceExecutor;

    public GithubExternalLinkClient(
            @Qualifier("githubRestClient") RestClient restClient,
            GithubProperties githubProperties,
            HttpResilienceExecutor resilienceExecutor) {
        this.restClient = restClient;
        this.githubProperties = githubProperties;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Override
    public boolean supports(URI url) {
        var host = url.getHost();
        if (host == null || !host.equalsIgnoreCase("github.com")) {
            return false;
        }

        return extractOwnerAndRepo(url).isPresent();
    }

    @Override
    public LinkCheckResult fetchUpdates(TrackedLink trackedLink) {
        var ownerRepo = extractOwnerAndRepo(trackedLink.url());
        if (ownerRepo.isEmpty()) {
            return LinkCheckResult.empty();
        }

        try {
            var responseBody =
                    resilienceExecutor.execute(CLIENT_NAME, () -> fetchIssuePayload(ownerRepo.orElseThrow()));
            return parseUpdates(responseBody, trackedLink);
        } catch (RestClientResponseException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "github")
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("status", exception.getStatusCode().value())
                    .log("GitHub request failed");
            return LinkCheckResult.failure();
        } catch (CallNotPermittedException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "github")
                    .addKeyValue("url", trackedLink.url())
                    .log("GitHub circuit breaker is open");
            return LinkCheckResult.failure();
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "github")
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("GitHub request failed with exception");
            return LinkCheckResult.failure();
        }
    }

    private String fetchIssuePayload(OwnerRepo source) {
        var request = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues")
                        .queryParam("state", "all")
                        .queryParam("sort", "created")
                        .queryParam("direction", "desc")
                        .queryParam("per_page", 100)
                        .build(source.owner(), source.repo()))
                .header("Accept", "application/vnd.github.text+json");
        if (StringUtils.hasText(githubProperties.getToken())) {
            request = request.header("Authorization", "Bearer " + githubProperties.getToken());
        }
        return request.retrieve().body(String.class);
    }

    private Optional<OwnerRepo> extractOwnerAndRepo(URI url) {
        var segments = Arrays.stream(url.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
        if (segments.size() != 2) {
            return Optional.empty();
        }

        return Optional.of(new OwnerRepo(segments.getFirst(), segments.get(1)));
    }

    @SuppressWarnings("unchecked")
    private LinkCheckResult parseUpdates(String responseBody, TrackedLink trackedLink) {
        if (responseBody == null || responseBody.isBlank()) {
            return LinkCheckResult.empty();
        }

        var parsed = JsonParserFactory.getJsonParser().parseList(responseBody);
        var events = new ArrayList<GithubEvent>();
        for (var item : parsed) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }

            var event = parseEvent((Map<String, Object>) itemMap);
            if (event == null || !isNewEvent(event, trackedLink)) {
                continue;
            }
            events.add(event);
        }

        if (events.isEmpty()) {
            return LinkCheckResult.empty();
        }

        events.sort(Comparator.comparing(GithubEvent::createdAt).thenComparingLong(GithubEvent::id));
        var updates = events.stream()
                .map(event -> new DetectedUpdate(
                        UpdateProvider.GITHUB,
                        event.eventType(),
                        event.title(),
                        event.author(),
                        event.createdAt(),
                        event.preview(),
                        Long.toString(event.id())))
                .toList();
        return new LinkCheckResult(Optional.of(updates.getLast().cursor()), updates, false);
    }

    private GithubEvent parseEvent(Map<String, Object> item) {
        var id = item.get("id");
        var createdAt = item.get("created_at");
        var title = item.get("title");
        if (!(id instanceof Number numericId)
                || !(createdAt instanceof String createdAtRaw)
                || !(title instanceof String titleRaw)) {
            return null;
        }

        var author = extractGithubAuthor(item);
        var preview = extractGithubPreview(item);
        var eventType = item.containsKey("pull_request") ? UpdateEventType.PULL_REQUEST : UpdateEventType.ISSUE;
        return new GithubEvent(
                numericId.longValue(), Instant.parse(createdAtRaw), eventType, titleRaw, author, preview);
    }

    private boolean isNewEvent(GithubEvent event, TrackedLink trackedLink) {
        if (trackedLink.lastEventAt() == null) {
            return event.createdAt().isAfter(trackedLink.lastUpdatedAt());
        }

        var compareByTime = event.createdAt().compareTo(trackedLink.lastEventAt());
        if (compareByTime != 0) {
            return compareByTime > 0;
        }

        return event.id() > parseGithubCursor(trackedLink.lastEventCursor());
    }

    @SuppressWarnings("unchecked")
    private String extractGithubAuthor(Map<String, Object> item) {
        var user = item.get("user");
        if (user instanceof Map<?, ?> userMap) {
            var login = ((Map<String, Object>) userMap).get("login");
            if (login instanceof String loginRaw && !loginRaw.isBlank()) {
                return loginRaw;
            }
        }
        return "unknown";
    }

    private String extractGithubPreview(Map<String, Object> item) {
        var bodyText = item.get("body_text");
        if (bodyText instanceof String bodyTextRaw && !bodyTextRaw.isBlank()) {
            return truncatePreview(bodyTextRaw);
        }

        var body = item.get("body");
        if (body instanceof String bodyRaw && !bodyRaw.isBlank()) {
            return truncatePreview(bodyRaw);
        }

        return "(empty)";
    }

    private long parseGithubCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MIN_VALUE;
        }
        return Long.parseLong(cursor);
    }

    private String truncatePreview(String rawPreview) {
        var normalized = rawPreview.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "(empty)";
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private record GithubEvent(
            long id, Instant createdAt, UpdateEventType eventType, String title, String author, String preview) {}

    private record OwnerRepo(String owner, String repo) {}
}
