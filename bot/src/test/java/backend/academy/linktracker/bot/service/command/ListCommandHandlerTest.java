package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.client.scrapper.ScrapperClientException;
import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.ListLinksResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListCommandHandlerTest {

    @Mock
    private ScrapperClient scrapperClient;

    @Test
    void returnsEmptyMessageWhenNoLinks() {
        var handler = new ListCommandHandler(scrapperClient);
        var request = new CommandRequest("/list", "", "/list", 42L, 7L);
        when(scrapperClient.getLinks(42L)).thenReturn(new ListLinksResponse(List.of(), 0));

        var response = handler.handle(request, List.of());

        assertEquals(ListCommandHandler.EMPTY_LIST_RESPONSE, response);
        verify(scrapperClient).getLinks(42L);
    }

    @Test
    void filtersLinksByTag() {
        var handler = new ListCommandHandler(scrapperClient);
        var request = new CommandRequest("/list", "work", "/list work", 42L, 7L);
        when(scrapperClient.getLinks(42L))
                .thenReturn(new ListLinksResponse(
                        List.of(
                                new LinkResponse(1L, "https://github.com/user/repo", List.of("work"), List.of()),
                                new LinkResponse(
                                        2L, "https://stackoverflow.com/questions/1", List.of("study"), List.of())),
                        2));

        var response = handler.handle(request, List.of());

        assertEquals(
                "Отслеживаемые ссылки с тегом 'work':" + System.lineSeparator() + "1. https://github.com/user/repo",
                response);
    }

    @Test
    void returnsFallbackMessageWhenScrapperFails() {
        var handler = new ListCommandHandler(scrapperClient);
        var request = new CommandRequest("/list", "", "/list", 42L, 7L);
        when(scrapperClient.getLinks(42L)).thenThrow(new ScrapperClientException(0, "boom"));

        var response = handler.handle(request, List.of());

        assertEquals(ListCommandHandler.SCRAPPER_UNAVAILABLE_RESPONSE, response);
    }

    @Test
    void returnsStartRequiredWhenChatIsNotRegistered() {
        var handler = new ListCommandHandler(scrapperClient);
        var request = new CommandRequest("/list", "", "/list", 42L, 7L);
        when(scrapperClient.getLinks(42L))
                .thenThrow(new ScrapperClientException(404, ScrapperClientException.CHAT_NOT_FOUND, "chat not found"));

        var response = handler.handle(request, List.of());

        assertEquals(ListCommandHandler.START_REQUIRED_RESPONSE, response);
    }
}
