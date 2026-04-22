package backend.academy.linktracker.scrapper.exception;

import java.io.Serial;

public class LinkAlreadyTrackedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LinkAlreadyTrackedException(long chatId, String link) {
        super("Link is already tracked for chat " + chatId + ": " + link);
    }
}
