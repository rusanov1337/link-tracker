package backend.academy.linktracker.scrapper.repository.support;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SupportedLinkCanonicalizer {

    private SupportedLinkCanonicalizer() {}

    public static URI canonicalize(URI url) {
        var normalized = url.normalize();
        var scheme = normalized.getScheme();
        var host = normalized.getHost();
        if (scheme == null || host == null) {
            return normalized;
        }

        var normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        var normalizedHost = host.toLowerCase(Locale.ROOT);
        var segments = pathSegments(normalized);
        var canonicalPath = canonicalPath(normalizedHost, segments)
                .orElseGet(() -> normalized.getRawPath() == null ? "" : normalized.getRawPath());

        return URI.create(buildAuthority(normalizedScheme, normalized, normalizedHost) + canonicalPath);
    }

    private static String buildAuthority(String scheme, URI normalized, String host) {
        var rawAuthority = new StringBuilder(scheme).append("://");
        if (normalized.getRawUserInfo() != null) {
            rawAuthority.append(normalized.getRawUserInfo()).append('@');
        }
        rawAuthority.append(host);
        if (normalized.getPort() >= 0) {
            rawAuthority.append(':').append(normalized.getPort());
        }
        return rawAuthority.toString();
    }

    private static Optional<String> canonicalPath(String host, List<String> segments) {
        return switch (host) {
            case "github.com" ->
                segments.size() == 2
                        ? Optional.of("/"
                                + segments.get(0).toLowerCase(Locale.ROOT)
                                + "/"
                                + segments.get(1).toLowerCase(Locale.ROOT))
                        : Optional.empty();
            case "stackoverflow.com" -> canonicalStackoverflowPath(segments);
            default -> Optional.empty();
        };
    }

    private static Optional<String> canonicalStackoverflowPath(List<String> segments) {
        if (segments.size() < 2) {
            return Optional.empty();
        }
        if ("questions".equals(segments.getFirst()) && isNumeric(segments.get(1))) {
            return Optional.of("/questions/" + segments.get(1));
        }
        if ("q".equals(segments.getFirst()) && isNumeric(segments.get(1))) {
            return Optional.of("/questions/" + segments.get(1));
        }
        return Optional.empty();
    }

    private static boolean isNumeric(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private static List<String> pathSegments(URI uri) {
        return Arrays.stream(uri.getPath().split("/"))
                .map(String::strip)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }
}
