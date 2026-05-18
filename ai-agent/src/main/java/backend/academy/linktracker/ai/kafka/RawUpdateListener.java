package backend.academy.linktracker.ai.kafka;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.service.UpdateGroupingService;
import backend.academy.linktracker.ai.service.UpdateProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawUpdateListener.class);

    private final UpdateProcessingService processingService;
    private final UpdateGroupingService groupingService;

    public RawUpdateListener(UpdateProcessingService processingService, UpdateGroupingService groupingService) {
        this.processingService = processingService;
        this.groupingService = groupingService;
    }

    @KafkaListener(topics = "${ai-agent.kafka.topics.raw-updates}")
    public void handle(RawLinkUpdateEvent update) {
        LOGGER.info("Raw update received id={}", update.id());
        processingService
                .process(update)
                .ifPresentOrElse(groupingService::submit, () -> LOGGER.info("Raw update filtered id={}", update.id()));
    }
}
