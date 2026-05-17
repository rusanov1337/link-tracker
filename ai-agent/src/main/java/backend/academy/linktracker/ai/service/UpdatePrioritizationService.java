package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class UpdatePrioritizationService {

    private final AiAgentProperties properties;

    public UpdatePrioritizationService(AiAgentProperties properties) {
        this.properties = properties;
    }

    public UpdatePriority prioritize(String description) {
        var normalizedDescription = description.toLowerCase(Locale.ROOT);
        if (containsAny(normalizedDescription, properties.getPrioritization().getHighKeywords())) {
            return UpdatePriority.HIGH;
        }
        if (containsAny(normalizedDescription, properties.getPrioritization().getLowKeywords())) {
            return UpdatePriority.LOW;
        }
        return UpdatePriority.MEDIUM;
    }

    private boolean containsAny(String description, Iterable<String> keywords) {
        for (var keyword : keywords) {
            if (description.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
