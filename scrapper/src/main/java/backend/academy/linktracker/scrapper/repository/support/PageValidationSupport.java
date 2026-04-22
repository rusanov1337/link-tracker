package backend.academy.linktracker.scrapper.repository.support;

public final class PageValidationSupport {

    private PageValidationSupport() {}

    public static void validatePage(int limit, int offset) {
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Page offset must be non-negative");
        }
    }
}
