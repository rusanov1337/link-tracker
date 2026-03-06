package backend.academy.linktracker.scrapper.exception;

public class LinkAlreadyTrackedException extends RuntimeException {

    public LinkAlreadyTrackedException(long chatId, String link) {
        super("Link is already tracked for chat " + chatId + ": " + link);
    }
}
