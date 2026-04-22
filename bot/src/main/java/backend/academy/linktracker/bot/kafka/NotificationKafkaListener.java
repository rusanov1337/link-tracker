package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationKafkaListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkUpdateNotificationService linkUpdateNotificationService;
    private final Validator validator;

    @KafkaListener(topics = "${app.kafka.topics.link-updates}")
    public void processLinkUpdate(String payload) {
        linkUpdateNotificationService.processStrict(readAndValidate(payload, LinkUpdate.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.processing-failure-reports}")
    public void processProcessingFailureReport(String payload) {
        linkUpdateNotificationService.processReportStrict(readAndValidate(payload, ProcessingFailureReport.class));
    }

    private <T> T readAndValidate(String payload, Class<T> type) {
        var message = read(payload, type);
        var violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return message;
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka notification payload is malformed", exception);
        }
    }
}
