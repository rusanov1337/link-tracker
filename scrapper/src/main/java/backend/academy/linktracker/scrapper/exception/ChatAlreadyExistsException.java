package backend.academy.linktracker.scrapper.exception;

import java.io.Serial;

public class ChatAlreadyExistsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChatAlreadyExistsException(long chatId) {
        super("Chat already exists: " + chatId);
    }
}
