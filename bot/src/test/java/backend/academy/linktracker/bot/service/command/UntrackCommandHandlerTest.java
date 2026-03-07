package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UntrackCommandHandlerTest {

    @Mock
    private ScrapperClient scrapperClient;

    private final LinkInputParser linkInputParser = new LinkInputParser();

    @Test
    void returnsUsageWhenLinkIsMissing() {
        var handler = new UntrackCommandHandler(scrapperClient, linkInputParser);
        var request = new CommandRequest("/untrack", "", "/untrack", 11L, 1L);

        var response = handler.handle(request, java.util.List.of());

        assertEquals(UntrackCommandHandler.USAGE_RESPONSE, response);
    }

    @Test
    void returnsInvalidLinkWhenLinkCannotBeParsed() {
        var handler = new UntrackCommandHandler(scrapperClient, linkInputParser);
        var request = new CommandRequest(
                "/untrack", "tbank://github.com/user/repo", "/untrack tbank://github.com/user/repo", 11L, 1L);

        var response = handler.handle(request, java.util.List.of());

        assertEquals(UntrackCommandHandler.INVALID_LINK_RESPONSE, response);
    }

    @Test
    void removesLinkWhenRequestIsValid() {
        var handler = new UntrackCommandHandler(scrapperClient, linkInputParser);
        var request = new CommandRequest(
                "/untrack", "https://github.com/user/repo", "/untrack https://github.com/user/repo", 11L, 1L);

        var response = handler.handle(request, java.util.List.of());

        assertEquals(UntrackCommandHandler.SUCCESS_RESPONSE, response);
        verify(scrapperClient).removeLink(11L, "https://github.com/user/repo");
    }

    @Test
    void returnsNotTrackedForNotFoundStatus() {
        var handler = new UntrackCommandHandler(scrapperClient, linkInputParser);
        var request = new CommandRequest(
                "/untrack", "https://github.com/user/repo", "/untrack https://github.com/user/repo", 11L, 1L);
        when(scrapperClient.removeLink(11L, "https://github.com/user/repo"))
                .thenThrow(new ScrapperClientException(404, ScrapperClientException.LINK_NOT_FOUND, "not found"));

        var response = handler.handle(request, java.util.List.of());

        assertEquals(UntrackCommandHandler.NOT_TRACKED_RESPONSE, response);
    }

    @Test
    void returnsStartRequiredWhenChatIsNotRegistered() {
        var handler = new UntrackCommandHandler(scrapperClient, linkInputParser);
        var request = new CommandRequest(
                "/untrack", "https://github.com/user/repo", "/untrack https://github.com/user/repo", 11L, 1L);
        when(scrapperClient.removeLink(11L, "https://github.com/user/repo"))
                .thenThrow(new ScrapperClientException(404, ScrapperClientException.CHAT_NOT_FOUND, "chat not found"));

        var response = handler.handle(request, java.util.List.of());

        assertEquals(UntrackCommandHandler.START_REQUIRED_RESPONSE, response);
    }
}
