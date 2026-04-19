package backend.academy.linktracker.scrapper.client.external;

import backend.academy.linktracker.scrapper.domain.DetectedUpdate;
import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.domain.UpdateEventType;
import backend.academy.linktracker.scrapper.domain.UpdateProvider;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.text.StringEscapeUtils;
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
public class StackoverflowExternalLinkClient implements ExternalLinkClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackoverflowExternalLinkClient.class);

    private final RestClient restClient;
    private final StackoverflowProperties stackoverflowProperties;

    public StackoverflowExternalLinkClient(
            @Qualifier("stackoverflowRestClient") RestClient restClient,
            StackoverflowProperties stackoverflowProperties) {
        this.restClient = restClient;
        this.stackoverflowProperties = stackoverflowProperties;
    }

    @Override
    public boolean supports(URI url) {
        var host = url.getHost();
        if (host == null || !host.equalsIgnoreCase("stackoverflow.com")) {
            return false;
        }

        return extractQuestionId(url).isPresent();
    }

    @Override
    public LinkCheckResult fetchUpdates(TrackedLink trackedLink) {
        var questionId = extractQuestionId(trackedLink.url());
        if (questionId.isEmpty()) {
            return LinkCheckResult.empty();
        }

        try {
            var questionIdValue = questionId.orElseThrow();
            var questionTitle = fetchQuestionTitle(questionIdValue);
            if (questionTitle.isEmpty()) {
                return LinkCheckResult.empty();
            }

            var updates = new ArrayList<StackoverflowEvent>();
            var answerPayload = fetchAnswerPayload(questionIdValue);
            updates.addAll(answerPayload.events());
            updates.addAll(fetchQuestionCommentEvents(questionIdValue));
            updates.addAll(fetchAnswerCommentEvents(answerPayload.answerIds()));
            updates.removeIf(event -> !isNewEvent(event, trackedLink));
            if (updates.isEmpty()) {
                return LinkCheckResult.empty();
            }

            updates.sort(Comparator.comparing(StackoverflowEvent::createdAt).thenComparing(this::compareEvents));
            var detectedUpdates = updates.stream()
                    .map(event -> new DetectedUpdate(
                            UpdateProvider.STACKOVERFLOW,
                            event.eventType(),
                            questionTitle.orElseThrow(),
                            event.author(),
                            event.createdAt(),
                            event.preview(),
                            event.cursor()))
                    .toList();
            return new LinkCheckResult(Optional.of(detectedUpdates.getLast().cursor()), detectedUpdates, false);
        } catch (RestClientResponseException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "stackoverflow")
                    .addKeyValue("url", trackedLink.url())
                    .addKeyValue("status", exception.getStatusCode().value())
                    .log("StackOverflow request failed");
            return LinkCheckResult.failure();
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "stackoverflow")
                    .addKeyValue("url", trackedLink.url())
                    .setCause(exception)
                    .log("StackOverflow request failed with exception");
            return LinkCheckResult.failure();
        }
    }

    private Optional<String> extractQuestionId(URI url) {
        var segments = Arrays.stream(url.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
        if (segments.size() < 2) {
            return Optional.empty();
        }

        int questionsIndex = segments.indexOf("questions");
        if (questionsIndex >= 0 && questionsIndex + 1 < segments.size()) {
            var questionId = segments.get(questionsIndex + 1);
            return isNumeric(questionId) ? Optional.of(questionId) : Optional.empty();
        }

        int shortIndex = segments.indexOf("q");
        if (shortIndex >= 0 && shortIndex + 1 < segments.size()) {
            var questionId = segments.get(shortIndex + 1);
            return isNumeric(questionId) ? Optional.of(questionId) : Optional.empty();
        }

        return Optional.empty();
    }

    private boolean isNumeric(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private Optional<String> fetchQuestionTitle(String questionId) {
        var responseBody = executeGet("/2.3/questions/{id}", questionId, false);
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        var firstItem = getFirstItem(responseBody);
        if (firstItem == null) {
            return Optional.empty();
        }

        var title = firstItem.get("title");
        if (!(title instanceof String titleRaw) || titleRaw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(titleRaw);
    }

    private AnswerPayload fetchAnswerPayload(String questionId) {
        var responseBody = executeGet("/2.3/questions/{id}/answers", questionId, true);
        return parseAnswerPayload(responseBody);
    }

    private List<StackoverflowEvent> fetchQuestionCommentEvents(String questionId) {
        var responseBody = executeGet("/2.3/questions/{id}/comments", questionId, true);
        return parseCommentEvents(responseBody, "question-comment");
    }

    private List<StackoverflowEvent> fetchAnswerCommentEvents(List<Long> answerIds) {
        if (answerIds.isEmpty()) {
            return List.of();
        }

        var encodedAnswerIds = answerIds.stream().map(String::valueOf).collect(Collectors.joining(";"));
        var responseBody = executeGet("/2.3/answers/{id}/comments", encodedAnswerIds, true);
        return parseCommentEvents(responseBody, "answer-comment");
    }

    private String executeGet(String path, String id, boolean includeBody) {
        return restClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path);
                    uriBuilder.queryParam("site", stackoverflowProperties.getSite());
                    if (includeBody) {
                        uriBuilder.queryParam("filter", "withbody");
                        uriBuilder.queryParam("sort", "creation");
                        uriBuilder.queryParam("order", "desc");
                    }
                    if (StringUtils.hasText(stackoverflowProperties.getKey())) {
                        uriBuilder.queryParam("key", stackoverflowProperties.getKey());
                    }
                    if (StringUtils.hasText(stackoverflowProperties.getAccessToken())) {
                        uriBuilder.queryParam("access_token", stackoverflowProperties.getAccessToken());
                    }
                    return uriBuilder.build(id);
                })
                .retrieve()
                .body(String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFirstItem(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(responseBody);
        var items = parsed.get("items");
        if (!(items instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return null;
        }
        var first = itemsList.getFirst();
        return first instanceof Map<?, ?> itemMap ? (Map<String, Object>) itemMap : null;
    }

    @SuppressWarnings("unchecked")
    private AnswerPayload parseAnswerPayload(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new AnswerPayload(List.of(), List.of());
        }

        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(responseBody);
        var items = parsed.get("items");
        if (!(items instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return new AnswerPayload(List.of(), List.of());
        }

        var events = new ArrayList<StackoverflowEvent>();
        var answerIds = new ArrayList<Long>();
        for (var item : itemsList) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }

            var answerId = ((Map<String, Object>) itemMap).get("answer_id");
            var createdAt = ((Map<String, Object>) itemMap).get("creation_date");
            if (!(answerId instanceof Number answerIdRaw) || !(createdAt instanceof Number createdAtRaw)) {
                continue;
            }

            answerIds.add(answerIdRaw.longValue());
            events.add(new StackoverflowEvent(
                    UpdateEventType.ANSWER,
                    answerIdRaw.longValue(),
                    Instant.ofEpochSecond(createdAtRaw.longValue()),
                    extractOwnerDisplayName((Map<String, Object>) itemMap),
                    extractBodyPreview((Map<String, Object>) itemMap),
                    "answer"));
        }
        return new AnswerPayload(events, answerIds);
    }

    @SuppressWarnings("unchecked")
    private List<StackoverflowEvent> parseCommentEvents(String responseBody, String cursorPrefix) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(responseBody);
        var items = parsed.get("items");
        if (!(items instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return List.of();
        }

        var events = new ArrayList<StackoverflowEvent>();
        for (var item : itemsList) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }

            var commentId = ((Map<String, Object>) itemMap).get("comment_id");
            var createdAt = ((Map<String, Object>) itemMap).get("creation_date");
            if (!(commentId instanceof Number commentIdRaw) || !(createdAt instanceof Number createdAtRaw)) {
                continue;
            }

            events.add(new StackoverflowEvent(
                    UpdateEventType.COMMENT,
                    commentIdRaw.longValue(),
                    Instant.ofEpochSecond(createdAtRaw.longValue()),
                    extractOwnerDisplayName((Map<String, Object>) itemMap),
                    extractBodyPreview((Map<String, Object>) itemMap),
                    cursorPrefix));
        }
        return events;
    }

    private boolean isNewEvent(StackoverflowEvent event, TrackedLink trackedLink) {
        if (trackedLink.lastEventAt() == null) {
            return event.createdAt().isAfter(trackedLink.lastUpdatedAt());
        }

        var compareByTime = event.createdAt().compareTo(trackedLink.lastEventAt());
        if (compareByTime != 0) {
            return compareByTime > 0;
        }

        return compareCursor(event.cursor(), trackedLink.lastEventCursor()) > 0;
    }

    @SuppressWarnings("unchecked")
    private String extractOwnerDisplayName(Map<String, Object> item) {
        var owner = item.get("owner");
        if (owner instanceof Map<?, ?> ownerMap) {
            var displayName = ((Map<String, Object>) ownerMap).get("display_name");
            if (displayName instanceof String displayNameRaw && !displayNameRaw.isBlank()) {
                return displayNameRaw;
            }
        }
        return "unknown";
    }

    private String extractBodyPreview(Map<String, Object> item) {
        var body = item.get("body");
        if (!(body instanceof String bodyRaw) || bodyRaw.isBlank()) {
            return "(empty)";
        }

        var text = StringEscapeUtils.unescapeHtml4(bodyRaw.replaceAll("<[^>]*>", " "));
        var normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "(empty)";
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private int compareEvents(StackoverflowEvent left, StackoverflowEvent right) {
        return compareCursor(left.cursor(), right.cursor());
    }

    private int compareCursor(String leftCursor, String rightCursor) {
        var left = parseCursor(leftCursor);
        var right = parseCursor(rightCursor);
        var typeCompare = Integer.compare(left.typeRank(), right.typeRank());
        if (typeCompare != 0) {
            return typeCompare;
        }
        return Long.compare(left.id(), right.id());
    }

    private Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(Integer.MIN_VALUE, Long.MIN_VALUE);
        }

        var parts = cursor.split(":", 2);
        if (parts.length != 2) {
            return new Cursor(Integer.MIN_VALUE, Long.MIN_VALUE);
        }

        var typeRank =
                switch (parts[0]) {
                    case "answer" -> 0;
                    case "question-comment" -> 1;
                    case "answer-comment" -> 2;
                    default -> Integer.MIN_VALUE;
                };
        return new Cursor(typeRank, Long.parseLong(parts[1]));
    }

    private record StackoverflowEvent(
            UpdateEventType eventType, long id, Instant createdAt, String author, String preview, String cursorPrefix) {
        private String cursor() {
            return cursorPrefix + ":" + id;
        }
    }

    private record AnswerPayload(List<StackoverflowEvent> events, List<Long> answerIds) {}

    private record Cursor(int typeRank, long id) {}
}
