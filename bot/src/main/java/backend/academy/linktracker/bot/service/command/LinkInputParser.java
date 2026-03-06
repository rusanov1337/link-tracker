package backend.academy.linktracker.bot.service.command;

import java.net.URI;
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

            var scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }

            return Optional.of(uri.toString());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
