package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.scrapper.client.bot.dto.LinkUpdateRequest;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "http", matchIfMissing = true)
public class HttpBotUpdatesClient implements BotUpdatesClient {

    private final RestClient restClient;

    public HttpBotUpdatesClient(@Qualifier("botRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        try {
            restClient
                    .post()
                    .uri("/updates")
                    .body(new LinkUpdateRequest(id, url.toString(), description, tgChatIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BotUpdatesClientException(
                    "Bot updates endpoint returned status "
                            + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException exception) {
            throw new BotUpdatesClientException("Bot updates endpoint call failed", exception);
        }
    }
}
