package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class UpdateFilterService {

    private final AiAgentProperties properties;

    public UpdateFilterService(AiAgentProperties properties) {
        this.properties = properties;
    }

    public boolean shouldProcess(RawLinkUpdateEvent update) {
        var description = normalize(update.description());
        return hasMinimumLength(description) && !containsStopWord(description) && !isExcludedAuthor(update.author());
    }

    private boolean hasMinimumLength(String description) {
        return description.length() >= properties.getFiltering().getMinLength();
    }

    private boolean containsStopWord(String description) {
        var normalizedDescription = description.toLowerCase(Locale.ROOT);
        return properties.getFiltering().getStopWords().stream()
                .map(this::normalize)
                .filter(stopWord -> !stopWord.isBlank())
                .map(stopWord -> stopWord.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedDescription::contains);
    }

    private boolean isExcludedAuthor(String author) {
        var normalizedAuthor = normalize(author);
        return properties.getFiltering().getExcludedAuthors().stream()
                .map(this::normalize)
                .anyMatch(excludedAuthor -> excludedAuthor.equalsIgnoreCase(normalizedAuthor));
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }
}
