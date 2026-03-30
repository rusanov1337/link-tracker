package backend.academy.linktracker.scrapper.client.bot;

import java.net.URI;
import java.util.List;

public interface BotUpdatesClient {

    void sendLinkUpdate(long id, URI url, String description, List<Long> tgChatIds);

    default void sendProcessingFailureReport(String description, List<Long> tgChatIds) {
        throw new UnsupportedOperationException("Processing failure reports are not supported for this transport");
    }
}
