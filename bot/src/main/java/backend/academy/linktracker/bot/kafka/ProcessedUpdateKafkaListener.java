package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.ai-agent", name = "enabled", havingValue = "true")
public class ProcessedUpdateKafkaListener {

    private final LinkUpdateNotificationService linkUpdateNotificationService;
    private final Validator validator;

    @KafkaListener(
            topics = "${app.kafka.topics.processed-updates}",
            containerFactory = "processedUpdateKafkaListenerContainerFactory")
    public void processProcessedUpdate(ProcessedLinkUpdateEvent payload) {
        linkUpdateNotificationService.processStrict(validate(toLinkUpdate(payload)));
    }

    private LinkUpdate toLinkUpdate(ProcessedLinkUpdateEvent payload) {
        return new LinkUpdate(payload.id(), payload.url(), payload.description(), List.copyOf(payload.tgChatIds()));
    }

    private <T> T validate(T message) {
        var violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return message;
    }
}
