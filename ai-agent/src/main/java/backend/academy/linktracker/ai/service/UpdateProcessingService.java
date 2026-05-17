package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UpdateProcessingService {

    private final UpdateFilterService filterService;
    private final UpdateSummarizer summarizer;
    private final UpdatePrioritizationService prioritizationService;

    public UpdateProcessingService(
            UpdateFilterService filterService,
            UpdateSummarizer summarizer,
            UpdatePrioritizationService prioritizationService) {
        this.filterService = filterService;
        this.summarizer = summarizer;
        this.prioritizationService = prioritizationService;
    }

    public Optional<ProcessedLinkUpdateEvent> process(RawLinkUpdateEvent update) {
        if (!filterService.shouldProcess(update)) {
            return Optional.empty();
        }
        return Optional.of(new ProcessedLinkUpdateEvent(
                update.id(),
                update.url(),
                summarizer.summarize(update.description()),
                update.tgChatIds(),
                prioritizationService.prioritize(update.description())));
    }
}
