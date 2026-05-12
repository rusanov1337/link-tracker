package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UpdateProcessingService {

    private final UpdateFilterService filterService;
    private final UpdateSummarizer summarizer;

    public UpdateProcessingService(UpdateFilterService filterService, UpdateSummarizer summarizer) {
        this.filterService = filterService;
        this.summarizer = summarizer;
    }

    public Optional<ProcessedLinkUpdateEvent> process(RawLinkUpdateEvent update) {
        if (!filterService.shouldProcess(update)) {
            return Optional.empty();
        }
        return Optional.of(new ProcessedLinkUpdateEvent(
                update.id(), summarizer.summarize(update.description()), update.tgChatIds(), UpdatePriority.HIGH));
    }
}
