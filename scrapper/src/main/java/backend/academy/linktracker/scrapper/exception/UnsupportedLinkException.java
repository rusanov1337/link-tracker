package backend.academy.linktracker.scrapper.exception;

public class UnsupportedLinkException extends RuntimeException {

    public UnsupportedLinkException(String link) {
        super("Link is not supported: " + link + ". Supported hosts: github.com, stackoverflow.com");
    }
}
