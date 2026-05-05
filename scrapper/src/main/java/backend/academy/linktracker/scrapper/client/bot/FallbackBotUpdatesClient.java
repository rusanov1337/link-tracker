package backend.academy.linktracker.scrapper.client.bot;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "http")
@ConditionalOnProperty(prefix = "app.bot.fallback", name = "kafka-enabled", havingValue = "true")
public class FallbackBotUpdatesClient implements BotUpdatesClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackBotUpdatesClient.class);

    private final HttpBotUpdatesClient primaryClient;
    private final KafkaBotUpdatesClient fallbackClient;

    public FallbackBotUpdatesClient(HttpBotUpdatesClient primaryClient, KafkaBotUpdatesClient fallbackClient) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        try {
            primaryClient.sendLinkUpdate(id, url, description, tgChatIds);
        } catch (BotUpdatesClientException primaryException) {
            LOGGER.atWarn()
                    .addKeyValue("transport", "http")
                    .addKeyValue("fallback", "kafka")
                    .setCause(primaryException)
                    .log("Primary bot updates transport failed, falling back to Kafka");
            sendLinkUpdateWithFallback(id, url, description, tgChatIds, primaryException);
        }
    }

    @Override
    public void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
        try {
            primaryClient.sendProcessingFailureReport(description, tgChatIds);
        } catch (BotUpdatesClientException primaryException) {
            LOGGER.atWarn()
                    .addKeyValue("transport", "http")
                    .addKeyValue("fallback", "kafka")
                    .setCause(primaryException)
                    .log("Primary bot reports transport failed, falling back to Kafka");
            sendProcessingFailureReportWithFallback(description, tgChatIds, primaryException);
        }
    }

    private void sendLinkUpdateWithFallback(
            long id, URI url, String description, List<Long> tgChatIds, BotUpdatesClientException primaryException) {
        try {
            fallbackClient.sendLinkUpdate(id, url, description, tgChatIds);
        } catch (BotUpdatesClientException fallbackException) {
            fallbackException.addSuppressed(primaryException);
            throw fallbackException;
        }
    }

    private void sendProcessingFailureReportWithFallback(
            String description, List<Long> tgChatIds, BotUpdatesClientException primaryException) {
        try {
            fallbackClient.sendProcessingFailureReport(description, tgChatIds);
        } catch (BotUpdatesClientException fallbackException) {
            fallbackException.addSuppressed(primaryException);
            throw fallbackException;
        }
    }
}
