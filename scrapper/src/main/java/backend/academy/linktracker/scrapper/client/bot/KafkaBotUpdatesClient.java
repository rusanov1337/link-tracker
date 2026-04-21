package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.scrapper.client.bot.dto.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.client.bot.dto.ProcessingFailureReportRequest;
import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaBotUpdatesClient implements BotUpdatesClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final NotificationKafkaProperties kafkaProperties;

    public KafkaBotUpdatesClient(
            KafkaTemplate<String, String> kafkaTemplate, NotificationKafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        var request = new LinkUpdateRequest(id, url.toString(), description, tgChatIds);
        send(kafkaProperties.getTopics().getLinkUpdates(), Long.toString(id), request);
    }

    @Override
    public void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
        var request = new ProcessingFailureReportRequest(description, tgChatIds);
        send(kafkaProperties.getTopics().getProcessingFailureReports(), buildReportKey(tgChatIds), request);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate
                    .send(topic, key, objectMapper.writeValueAsString(payload))
                    .get();
        } catch (JsonProcessingException exception) {
            throw new BotUpdatesClientException("Kafka notification serialization failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BotUpdatesClientException("Kafka notification publishing was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new BotUpdatesClientException("Kafka notification publishing failed", exception);
        }
    }

    private String buildReportKey(List<Long> tgChatIds) {
        if (tgChatIds.isEmpty()) {
            return "processing-failure-report";
        }

        return "processing-failure-report:" + tgChatIds.getFirst();
    }
}
