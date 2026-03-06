package backend.academy.linktracker.scrapper.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(long chatId, String link) {
        super("Link is not tracked for chat " + chatId + ": " + link);
    }
}
