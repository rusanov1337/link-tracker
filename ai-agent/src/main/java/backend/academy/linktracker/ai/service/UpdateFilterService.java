package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.regex.Pattern;
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
        return properties.getFiltering().getStopWords().stream()
                .map(this::normalize)
                .map(String::trim)
                .filter(stopWord -> !stopWord.isBlank())
                .map(this::stopWordPattern)
                .anyMatch(pattern -> pattern.matcher(description).find());
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

    private Pattern stopWordPattern(String stopWord) {
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + Pattern.quote(stopWord) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
