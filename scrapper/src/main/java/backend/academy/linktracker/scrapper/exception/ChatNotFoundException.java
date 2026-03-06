package backend.academy.linktracker.scrapper.exception;

public class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException(long chatId) {
        super("Chat does not exist: " + chatId);
    }
}
