package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import org.springframework.stereotype.Service;

@Service
public class UpdateSummarizer {

    private static final String SUMMARY_SUFFIX = "...";

    private final AiAgentProperties properties;

    public UpdateSummarizer(AiAgentProperties properties) {
        this.properties = properties;
    }

    public String summarize(String description) {
        var safeDescription = description == null ? "" : description;
        var threshold = properties.getSummarization().getThreshold();
        if (safeDescription.length() <= threshold) {
            return safeDescription;
        }
        return safeDescription.substring(0, threshold) + SUMMARY_SUFFIX;
    }
}
