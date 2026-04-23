package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationKafkaListener {

    private final LinkUpdateNotificationService linkUpdateNotificationService;
    private final Validator validator;

    @KafkaListener(topics = "${app.kafka.topics.link-updates}")
    public void processLinkUpdate(LinkUpdateEvent payload) {
        linkUpdateNotificationService.processStrict(validate(toLinkUpdate(payload)));
    }

    @KafkaListener(topics = "${app.kafka.topics.processing-failure-reports}")
    public void processProcessingFailureReport(ProcessingFailureReportEvent payload) {
        linkUpdateNotificationService.processReportStrict(validate(toProcessingFailureReport(payload)));
    }

    private LinkUpdate toLinkUpdate(LinkUpdateEvent payload) {
        return new LinkUpdate(
                payload.getId(), payload.getUrl(), payload.getDescription(), List.copyOf(payload.getTgChatIds()));
    }

    private ProcessingFailureReport toProcessingFailureReport(ProcessingFailureReportEvent payload) {
        return new ProcessingFailureReport(payload.getDescription(), List.copyOf(payload.getTgChatIds()));
    }

    private <T> T validate(T message) {
        var violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return message;
    }
}
