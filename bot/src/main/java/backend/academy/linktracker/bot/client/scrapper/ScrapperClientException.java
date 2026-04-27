package backend.academy.linktracker.bot.client.scrapper;

import java.io.Serial;

public class ScrapperClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CHAT_NOT_FOUND = "ChatNotFoundException";
    public static final String LINK_NOT_FOUND = "LinkNotFoundException";
    public static final String LINK_ALREADY_TRACKED = "LinkAlreadyTrackedException";

    private final int statusCode;
    private final String errorCode;

    public ScrapperClientException(int statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ScrapperClientException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ScrapperClientException(int statusCode, String message, Throwable cause) {
        this(statusCode, null, message, cause);
    }

    public ScrapperClientException(int statusCode, String message) {
        this(statusCode, null, message);
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean hasStatus(int expectedStatus) {
        return statusCode == expectedStatus;
    }

    public boolean hasErrorCode(String expectedErrorCode) {
        return expectedErrorCode != null && expectedErrorCode.equals(errorCode);
    }

    public boolean isChatNotFound() {
        return hasErrorCode(CHAT_NOT_FOUND);
    }

    public boolean isLinkNotFound() {
        return hasErrorCode(LINK_NOT_FOUND);
    }

    public boolean isLinkAlreadyTracked() {
        return hasErrorCode(LINK_ALREADY_TRACKED);
    }
}
