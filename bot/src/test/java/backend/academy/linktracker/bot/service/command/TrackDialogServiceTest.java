package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackDialogServiceTest {

    @Mock
    private ScrapperClient scrapperClient;

    private final LinkInputParser linkInputParser = new LinkInputParser();

    @Test
    void successfulDialogAddsLink() {
        var service = new TrackDialogService(scrapperClient, linkInputParser);
        when(scrapperClient.addLink(100L, "https://github.com/user/repo", List.of("work"), List.of()))
                .thenReturn(new LinkResponse(1L, "https://github.com/user/repo", List.of("work"), List.of()));

        assertEquals(TrackDialogService.START_PROMPT, service.start(100L));
        assertEquals(
                TrackDialogService.TAGS_PROMPT,
                service.handleUserInput(100L, "https://github.com/user/repo").orElseThrow());
        assertEquals(
                TrackDialogService.FILTERS_PROMPT,
                service.handleUserInput(100L, "work").orElseThrow());
        assertEquals(
                TrackDialogService.SUCCESS_RESPONSE,
                service.handleUserInput(100L, "-").orElseThrow());
        assertTrue(service.handleUserInput(100L, "anything").isEmpty());

        verify(scrapperClient).ensureChatRegistered(100L);
        verify(scrapperClient).addLink(100L, "https://github.com/user/repo", List.of("work"), List.of());
    }

    @Test
    void invalidLinkKeepsDialogOnLinkStep() {
        var service = new TrackDialogService(scrapperClient, linkInputParser);
        service.start(100L);

        var response = service.handleUserInput(100L, "tbank://github.com/user/repo");

        assertEquals(TrackDialogService.LINK_INVALID_RESPONSE, response.orElseThrow());
        assertTrue(service.isActive(100L));
    }

    @Test
    void duplicateLinkReturnsExpectedMessageAndFinishesDialog() {
        var service = new TrackDialogService(scrapperClient, linkInputParser);
        when(scrapperClient.addLink(100L, "https://github.com/user/repo", List.of(), List.of()))
                .thenThrow(new ScrapperClientException(409, "duplicate"));
        service.start(100L);
        service.handleUserInput(100L, "https://github.com/user/repo");
        service.handleUserInput(100L, "-");

        var response = service.handleUserInput(100L, "-");

        assertEquals(TrackDialogService.DUPLICATE_RESPONSE, response.orElseThrow());
        assertTrue(service.handleUserInput(100L, "after").isEmpty());
    }
}
