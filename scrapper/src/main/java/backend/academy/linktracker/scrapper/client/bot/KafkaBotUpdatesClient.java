package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaBotUpdatesClient implements BotUpdatesClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaBotUpdatesClient.class);

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
        kafkaTemplate.send(topic, key, payload).whenComplete((result, exception) -> {
            if (exception != null) {
                LOGGER.atWarn()
                        .addKeyValue("topic", topic)
                        .addKeyValue("key", key)
                        .setCause(exception)
                        .log("Kafka notification publishing failed");
            }
        });
    }

    private String buildReportKey(List<Long> tgChatIds) {
        if (tgChatIds.isEmpty()) {
            return "processing-failure-report";
        }

        return "processing-failure-report:" + tgChatIds.getFirst();
    }
}
