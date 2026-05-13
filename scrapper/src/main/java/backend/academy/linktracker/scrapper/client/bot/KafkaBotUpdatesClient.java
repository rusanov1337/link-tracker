package backend.academy.linktracker.scrapper.client.bot;

import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import backend.academy.linktracker.scrapper.client.bot.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${app.bot.transport:kafka}' == 'kafka' || '${app.bot.fallback.kafka-enabled:false}' == 'true'")
public class KafkaBotUpdatesClient implements BotUpdatesClient {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;
    private final NotificationKafkaProperties kafkaProperties;

    public KafkaBotUpdatesClient(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("jsonKafkaTemplate") KafkaTemplate<String, Object> jsonKafkaTemplate,
            NotificationKafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds) {
        if (kafkaProperties.getAiAgent().isEnabled()) {
            send(
                    jsonKafkaTemplate,
                    kafkaProperties.getTopics().getRawUpdates(),
                    Long.toString(id),
                    new RawLinkUpdateEvent(
                            id, url.toString(), description, extractAuthor(description), List.copyOf(tgChatIds)));
            return;
        }

        send(
                kafkaTemplate,
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
                kafkaTemplate,
                kafkaProperties.getTopics().getProcessingFailureReports(),
                buildReportKey(tgChatIds),
                ProcessingFailureReportEvent.newBuilder()
                        .setDescription(description)
                        .setTgChatIds(List.copyOf(tgChatIds))
                        .build());
    }

    private void send(KafkaTemplate<String, Object> template, String topic, String key, Object payload) {
        try {
            template.send(topic, key, payload).get();
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

    private String extractAuthor(String description) {
        for (var line : description.lines().toList()) {
            if (line.startsWith("Пользователь: ")) {
                return line.substring("Пользователь: ".length());
            }
        }

        return "";
    }
}
