package backend.academy.linktracker.ai.kafka;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.properties.AiAgentKafkaProperties;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class ProcessedUpdateProducer {

    private final KafkaTemplate<String, ProcessedLinkUpdateEvent> kafkaTemplate;
    private final AiAgentKafkaProperties kafkaProperties;

    public ProcessedUpdateProducer(
            KafkaTemplate<String, ProcessedLinkUpdateEvent> kafkaTemplate, AiAgentKafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    public CompletableFuture<SendResult<String, ProcessedLinkUpdateEvent>> send(ProcessedLinkUpdateEvent update) {
        return kafkaTemplate.send(
                kafkaProperties.getTopics().getProcessedUpdates(), Long.toString(update.id()), update);
    }
}
