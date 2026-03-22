package backend.academy.linktracker.bot.service.command;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LinkInputParser {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String GITHUB_HOST = "github.com";
    private static final String STACKOVERFLOW_HOST = "stackoverflow.com";
    private static final String STACKOVERFLOW_QUESTIONS_PATH = "questions";
    private static final String STACKOVERFLOW_SHORT_QUESTION_PATH = "q";

    public Optional<String> parseHttpUrl(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        var candidate = raw.strip();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        try {
            var uri = URI.create(candidate).normalize();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null) {
                return Optional.empty();
            }

            var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals(HTTP_SCHEME) && !scheme.equals(HTTPS_SCHEME)) {
                return Optional.empty();
            }

            var host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!isSupportedLink(host, uri)) {
                return Optional.empty();
            }

            return Optional.of(canonicalizeSupportedLink(uri, host));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean isSupportedLink(String host, URI uri) {
        return switch (host) {
            case GITHUB_HOST -> isGithubRepository(uri);
            case STACKOVERFLOW_HOST -> isStackoverflowQuestion(uri);
            default -> false;
        };
    }

    private boolean isGithubRepository(URI uri) {
        return pathSegments(uri).size() == 2;
    }

    private boolean isStackoverflowQuestion(URI uri) {
        var segments = pathSegments(uri);
        if (segments.size() < 2) {
            return false;
        }

        if (STACKOVERFLOW_QUESTIONS_PATH.equals(segments.getFirst())) {
            return isNumeric(segments.get(1));
        }

        return STACKOVERFLOW_SHORT_QUESTION_PATH.equals(segments.getFirst()) && isNumeric(segments.get(1));
    }

    private boolean isNumeric(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private java.util.List<String> pathSegments(URI uri) {
        return Arrays.stream(uri.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private String canonicalizeSupportedLink(URI uri, String host) {
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        var segments = pathSegments(uri);
        return switch (host) {
            case GITHUB_HOST ->
                scheme
                        + "://"
                        + host
                        + "/"
                        + segments.get(0).toLowerCase(Locale.ROOT)
                        + "/"
                        + segments.get(1).toLowerCase(Locale.ROOT);
            case STACKOVERFLOW_HOST ->
                scheme + "://" + host + "/" + STACKOVERFLOW_QUESTIONS_PATH + "/" + segments.get(1);
            default -> uri.toString();
        };
    }
}
