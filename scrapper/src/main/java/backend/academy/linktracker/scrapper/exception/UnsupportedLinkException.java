package backend.academy.linktracker.scrapper.exception;

import java.io.Serial;

public class UnsupportedLinkException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedLinkException(String link) {
        super("Link is not supported: " + link + ". Supported hosts: github.com, stackoverflow.com");
    }
}
