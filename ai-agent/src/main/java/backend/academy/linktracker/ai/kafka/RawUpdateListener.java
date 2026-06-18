package backend.academy.linktracker.ai.kafka;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.service.UpdateProcessingService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawUpdateListener {

    private final UpdateProcessingService processingService;
    private final ProcessedUpdateProducer producer;
    private final Validator validator;

    @KafkaListener(topics = "${ai-agent.kafka.topics.raw-updates}")
    public void handle(RawLinkUpdateEvent update) {
        if (update == null) {
            log.warn("Null raw update skipped");
            return;
        }

        var violations = validator.validate(update);
        if (!violations.isEmpty()) {
            log.atWarn()
                    .addKeyValue("updateId", update.id())
                    .addKeyValue("violations", violations)
                    .log("Invalid raw update skipped");
            return;
        }

        log.info("Raw update received id={}", update.id());
        processingService
                .process(update)
                .ifPresentOrElse(producer::send, () -> log.info("Raw update filtered id={}", update.id()));
    }
}
