package backend.academy.linktracker.scrapper.client.bot;

import java.io.Serial;

public class BotUpdatesClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BotUpdatesClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
