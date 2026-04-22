package backend.academy.linktracker.bot.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaListenerTest {

    @Mock
    private LinkUpdateNotificationService linkUpdateNotificationService;

    private NotificationKafkaListener listener;

    @BeforeEach
    void setUp() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        listener = new NotificationKafkaListener(linkUpdateNotificationService, validator);
    }

    @Test
    void processLinkUpdatePassesValidPayloadToNotificationService() {
        listener.processLinkUpdate("""
                {
                  "id": 42,
                  "url": "https://github.com/octocat/hello-world",
                  "description": "Updated",
                  "tgChatIds": [1, 2]
                }
                """);

        var captor = ArgumentCaptor.forClass(LinkUpdate.class);
        verify(linkUpdateNotificationService).processStrict(captor.capture());
        assertEquals(
                new LinkUpdate(42L, "https://github.com/octocat/hello-world", "Updated", List.of(1L, 2L)),
                captor.getValue());
    }

    @Test
    void processProcessingFailureReportPassesValidPayloadToNotificationService() {
        listener.processProcessingFailureReport("""
                {
                  "description": "Failed links",
                  "tgChatIds": [1, 2]
                }
                """);

        var captor = ArgumentCaptor.forClass(ProcessingFailureReport.class);
        verify(linkUpdateNotificationService).processReportStrict(captor.capture());
        assertEquals(new ProcessingFailureReport("Failed links", List.of(1L, 2L)), captor.getValue());
    }

    @Test
    void processLinkUpdateRejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> listener.processLinkUpdate("{\"id\":"));

        verifyNoInteractions(linkUpdateNotificationService);
    }

    @Test
    void processLinkUpdateRejectsInvalidPayload() {
        assertThrows(ConstraintViolationException.class, () -> listener.processLinkUpdate("""
                {
                  "id": 42,
                  "url": "not-url",
                  "description": "",
                  "tgChatIds": []
                }
                """));

        verifyNoInteractions(linkUpdateNotificationService);
    }
}
