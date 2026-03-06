package backend.academy.linktracker.scrapper.client.external;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
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

    private final RestClient restClient;
    private final GithubProperties githubProperties;

    public GithubExternalLinkClient(
            @Qualifier("githubRestClient") RestClient restClient, GithubProperties githubProperties) {
        this.restClient = restClient;
        this.githubProperties = githubProperties;
    }

    @Override
    public boolean supports(URI url) {
        var host = url.getHost();
        return host != null && host.equalsIgnoreCase("github.com");
    }

    @Override
    public Optional<Instant> fetchLastUpdated(URI url) {
        var ownerRepo = extractOwnerAndRepo(url);
        if (ownerRepo.isEmpty()) {
            return Optional.empty();
        }

        try {
            var source = ownerRepo.orElseThrow();
            var request = restClient.get().uri("/repos/{owner}/{repo}", source.owner(), source.repo());
            if (StringUtils.hasText(githubProperties.getToken())) {
                request = request.header("Authorization", "Bearer " + githubProperties.getToken());
            }
            var responseBody = request.retrieve().body(String.class);
            return parseUpdatedAt(responseBody);
        } catch (RestClientResponseException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "github")
                    .addKeyValue("url", url)
                    .addKeyValue("status", exception.getStatusCode().value())
                    .log("GitHub request failed");
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.atWarn()
                    .addKeyValue("provider", "github")
                    .addKeyValue("url", url)
                    .setCause(exception)
                    .log("GitHub request failed with exception");
            return Optional.empty();
        }
    }

    private Optional<OwnerRepo> extractOwnerAndRepo(URI url) {
        var segments = Arrays.stream(url.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
        if (segments.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(new OwnerRepo(segments.getFirst(), segments.get(1)));
    }

    private Optional<Instant> parseUpdatedAt(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(responseBody);
        var updatedAt = parsed.get("updated_at");
        if (!(updatedAt instanceof String updatedAtRaw) || updatedAtRaw.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Instant.parse(updatedAtRaw));
    }

    private record OwnerRepo(String owner, String repo) {}
}
