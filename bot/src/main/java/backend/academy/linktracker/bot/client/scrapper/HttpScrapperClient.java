package backend.academy.linktracker.bot.client.scrapper;

import backend.academy.linktracker.bot.client.http.HttpResilienceExecutor;
import backend.academy.linktracker.bot.client.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.bot.client.scrapper.dto.ApiErrorResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.RemoveLinkRequest;
import backend.academy.linktracker.bot.service.BotMetricsService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "http", matchIfMissing = true)
public class HttpScrapperClient implements ScrapperClient {

    private static final String CLIENT_NAME = "scrapper";

    private final RestClient restClient;
    private final HttpResilienceExecutor resilienceExecutor;
    private final BotMetricsService botMetricsService;

    public HttpScrapperClient(
            @Qualifier("scrapperRestClient") RestClient restClient,
            HttpResilienceExecutor resilienceExecutor,
            BotMetricsService botMetricsService) {
        this.restClient = restClient;
        this.resilienceExecutor = resilienceExecutor;
        this.botMetricsService = botMetricsService;
    }

    @Override
    public void ensureChatRegistered(long chatId) {
        var startNanos = System.nanoTime();
        try {
            resilienceExecutor.execute(CLIENT_NAME, () -> {
                restClient.post().uri("/tg-chat/{id}", chatId).retrieve().toBodilessEntity();
                return null;
            });
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                return;
            }
            throw toScrapperClientException(exception);
        } catch (CallNotPermittedException exception) {
            throw new ScrapperClientException(0, "Scrapper circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new ScrapperClientException(0, "Failed to register chat in scrapper", exception);
        } finally {
            botMetricsService.recordCommandDuration(
                    "scrapper_sync_api", "registerChat", System.nanoTime() - startNanos);
        }
    }

    @Override
    public ListLinksResponse getLinks(long chatId) {
        var startNanos = System.nanoTime();
        try {
            var response = resilienceExecutor.execute(CLIENT_NAME, () -> restClient
                    .get()
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .retrieve()
                    .body(ListLinksResponse.class));
            return response == null ? new ListLinksResponse(List.of(), 0) : response;
        } catch (RestClientResponseException exception) {
            throw toScrapperClientException(exception);
        } catch (CallNotPermittedException exception) {
            throw new ScrapperClientException(0, "Scrapper circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new ScrapperClientException(0, "Failed to list links in scrapper", exception);
        } finally {
            botMetricsService.recordCommandDuration("scrapper_sync_api", "getLinks", System.nanoTime() - startNanos);
        }
    }

    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        var startNanos = System.nanoTime();
        try {
            return resilienceExecutor.execute(CLIENT_NAME, () -> restClient
                    .post()
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .body(new AddLinkRequest(link, tags, filters))
                    .retrieve()
                    .body(LinkResponse.class));
        } catch (RestClientResponseException exception) {
            throw toScrapperClientException(exception);
        } catch (CallNotPermittedException exception) {
            throw new ScrapperClientException(0, "Scrapper circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new ScrapperClientException(0, "Failed to add link in scrapper", exception);
        } finally {
            botMetricsService.recordCommandDuration("scrapper_sync_api", "addLink", System.nanoTime() - startNanos);
        }
    }

    @Override
    public LinkResponse removeLink(long chatId, String link) {
        var startNanos = System.nanoTime();
        try {
            return resilienceExecutor.execute(CLIENT_NAME, () -> restClient
                    .method(HttpMethod.DELETE)
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .body(new RemoveLinkRequest(link))
                    .retrieve()
                    .body(LinkResponse.class));
        } catch (RestClientResponseException exception) {
            throw toScrapperClientException(exception);
        } catch (CallNotPermittedException exception) {
            throw new ScrapperClientException(0, "Scrapper circuit breaker is open", exception);
        } catch (RestClientException exception) {
            throw new ScrapperClientException(0, "Failed to remove link in scrapper", exception);
        } finally {
            botMetricsService.recordCommandDuration("scrapper_sync_api", "removeLink", System.nanoTime() - startNanos);
        }
    }

    private ScrapperClientException toScrapperClientException(RestClientResponseException exception) {
        var errorResponse = parseErrorResponse(exception);
        var description = errorResponse != null && errorResponse.description() != null
                ? errorResponse.description()
                : "Scrapper responded with status " + exception.getStatusCode().value();
        var errorCode = errorResponse != null ? errorResponse.exceptionName() : null;
        return new ScrapperClientException(exception.getStatusCode().value(), errorCode, description, exception);
    }

    private ApiErrorResponse parseErrorResponse(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(ApiErrorResponse.class);
        } catch (RestClientException responseParsingException) {
            return null;
        }
    }
}
