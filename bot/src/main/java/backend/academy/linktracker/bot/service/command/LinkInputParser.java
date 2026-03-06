package backend.academy.linktracker.bot.service.command;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LinkInputParser {

    private static final Set<String> SUPPORTED_HOSTS = Set.of("github.com", "stackoverflow.com");

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
            if (!SUPPORTED_HOSTS.contains(host)) {
                return Optional.empty();
            }

            return Optional.of(uri.toString());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
