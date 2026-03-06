package backend.academy.linktracker.scrapper.exception;

public class ChatAlreadyExistsException extends RuntimeException {

    public ChatAlreadyExistsException(long chatId) {
        super("Chat already exists: " + chatId);
    }
}
