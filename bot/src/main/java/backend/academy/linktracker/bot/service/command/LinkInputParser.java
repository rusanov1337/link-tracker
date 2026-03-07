package backend.academy.linktracker.bot.service.command;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LinkInputParser {

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
            if (!scheme.equals("http") && !scheme.equals("https")) {
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
            case "github.com" -> isGithubRepository(uri);
            case "stackoverflow.com" -> isStackoverflowQuestion(uri);
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

        if ("questions".equals(segments.getFirst())) {
            return isNumeric(segments.get(1));
        }

        return "q".equals(segments.getFirst()) && isNumeric(segments.get(1));
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
            case "github.com" -> scheme + "://" + host + "/" + segments.get(0) + "/" + segments.get(1);
            case "stackoverflow.com" -> scheme + "://" + host + "/questions/" + segments.get(1);
            default -> uri.toString();
        };
    }
}
