package backend.academy.linktracker.scrapper.client.external;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
        return host != null && host.equalsIgnoreCase("stackoverflow.com");
    }

    @Override
    public Optional<Instant> fetchLastUpdated(URI url) {
        var questionId = extractQuestionId(url);
        if (questionId.isEmpty()) {
            return Optional.empty();
        }

        try {
            var responseBody = restClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/2.3/questions/{id}");
                        uriBuilder.queryParam("site", stackoverflowProperties.getSite());
                        if (StringUtils.hasText(stackoverflowProperties.getKey())) {
                            uriBuilder.queryParam("key", stackoverflowProperties.getKey());
                        }
                        if (StringUtils.hasText(stackoverflowProperties.getAccessToken())) {
                            uriBuilder.queryParam("access_token", stackoverflowProperties.getAccessToken());
                        }
                        return uriBuilder.build(questionId.orElseThrow());
                    })
                    .retrieve()
                    .body(String.class);
            return parseLastActivityDate(responseBody);
        } catch (RestClientResponseException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "stackoverflow")
                    .addKeyValue("url", url)
                    .addKeyValue("status", exception.getStatusCode().value())
                    .log("StackOverflow request failed");
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "stackoverflow")
                    .addKeyValue("url", url)
                    .setCause(exception)
                    .log("StackOverflow request failed with exception");
            return Optional.empty();
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

    @SuppressWarnings("unchecked")
    private Optional<Instant> parseLastActivityDate(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(responseBody);
        var items = parsed.get("items");
        if (!(items instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return Optional.empty();
        }
        var first = itemsList.getFirst();
        if (!(first instanceof Map<?, ?> itemMap)) {
            return Optional.empty();
        }
        var lastActivity = itemMap.get("last_activity_date");
        if (!(lastActivity instanceof Number epochSeconds)) {
            return Optional.empty();
        }

        return Optional.of(Instant.ofEpochSecond(epochSeconds.longValue()));
    }
}
