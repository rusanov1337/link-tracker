package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaBotUpdatesClient implements BotUpdatesClient {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationKafkaProperties kafkaProperties;

    public KafkaBotUpdatesClient(
            KafkaTemplate<String, Object> kafkaTemplate, NotificationKafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        send(
                kafkaProperties.getTopics().getLinkUpdates(),
                Long.toString(id),
                LinkUpdateEvent.newBuilder()
                        .setId(id)
                        .setUrl(url.toString())
                        .setDescription(description)
                        .setTgChatIds(List.copyOf(tgChatIds))
                        .build());
    }

    @Override
    public void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
        send(
                kafkaProperties.getTopics().getProcessingFailureReports(),
                buildReportKey(tgChatIds),
                ProcessingFailureReportEvent.newBuilder()
                        .setDescription(description)
                        .setTgChatIds(List.copyOf(tgChatIds))
                        .build());
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get();
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
