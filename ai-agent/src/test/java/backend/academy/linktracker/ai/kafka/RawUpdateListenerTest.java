package backend.academy.linktracker.ai.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.service.UpdateGroupingService;
import backend.academy.linktracker.ai.service.UpdateProcessingService;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawUpdateListenerTest {

    private final UpdateProcessingService processingService = mock(UpdateProcessingService.class);
    private final UpdateGroupingService groupingService = mock(UpdateGroupingService.class);

    @Test
    void skipsInvalidUpdateWithoutProcessingOrPublishing() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var listener = new RawUpdateListener(processingService, groupingService, validatorFactory.getValidator());
            var invalidUpdate = new RawLinkUpdateEvent(0L, "", "", "", List.of());

            listener.handle(invalidUpdate);

            verifyNoInteractions(processingService, groupingService);
        }
    }

    @Test
    void skipsNullUpdateWithoutProcessingOrPublishing() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var listener = new RawUpdateListener(processingService, groupingService, validatorFactory.getValidator());

            listener.handle(null);

            verifyNoInteractions(processingService, groupingService);
        }
    }
}
