package backend.academy.linktracker.bot.client.scrapper;

import backend.academy.linktracker.bot.client.scrapper.dto.LinkResponse;
import backend.academy.linktracker.bot.client.scrapper.dto.ListLinksResponse;
import java.util.List;

public interface ScrapperClient {

    void ensureChatRegistered(long chatId);

    ListLinksResponse getLinks(long chatId);

    LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters);

    LinkResponse removeLink(long chatId, String link);
}
