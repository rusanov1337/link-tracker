package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.scrapper.client.bot.dto.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.client.bot.dto.ProcessingFailureReportRequest;
import backend.academy.linktracker.scrapper.client.http.HttpResilienceExecutor;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "http")
public class HttpBotUpdatesClient implements BotUpdatesClient {

    private static final String CLIENT_NAME = "bot-updates";

    private final RestClient restClient;
    private final HttpResilienceExecutor resilienceExecutor;

    public HttpBotUpdatesClient(
            @Qualifier("botRestClient") RestClient restClient, HttpResilienceExecutor resilienceExecutor) {
        this.restClient = restClient;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        try {
            resilienceExecutor.execute(CLIENT_NAME, () -> {
                restClient
                        .post()
                        .uri("/updates")
                        .body(new LinkUpdateRequest(id, url.toString(), description, tgChatIds))
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
        } catch (RestClientResponseException exception) {
            throw new BotUpdatesClientException(
                    "Bot updates endpoint returned status "
                            + exception.getStatusCode().value(),
                    exception);
        } catch (CallNotPermittedException exception) {
            throw new BotUpdatesClientException("Bot updates circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new BotUpdatesClientException("Bot updates endpoint call failed", exception);
        }
    }

    @Override
    public void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
        try {
            resilienceExecutor.execute(CLIENT_NAME, () -> {
                restClient
                        .post()
                        .uri("/reports")
                        .body(new ProcessingFailureReportRequest(description, tgChatIds))
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
        } catch (RestClientResponseException exception) {
            throw new BotUpdatesClientException(
                    "Bot reports endpoint returned status "
                            + exception.getStatusCode().value(),
                    exception);
        } catch (CallNotPermittedException exception) {
            throw new BotUpdatesClientException("Bot updates circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new BotUpdatesClientException("Bot reports endpoint call failed", exception);
        }
    }
}
