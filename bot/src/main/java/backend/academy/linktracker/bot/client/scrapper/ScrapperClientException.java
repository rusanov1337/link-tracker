package backend.academy.linktracker.bot.client.scrapper;

public class ScrapperClientException extends RuntimeException {

    private final int statusCode;

    public ScrapperClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public ScrapperClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean hasStatus(int expectedStatus) {
        return statusCode == expectedStatus;
    }
}
