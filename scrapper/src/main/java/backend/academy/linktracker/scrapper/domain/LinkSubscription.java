package backend.academy.linktracker.scrapper.domain;

import java.util.List;
import java.util.Objects;

public record LinkSubscription(long chatId, long linkId, List<String> tags, List<String> filters) {

    public LinkSubscription {
        tags = normalize(tags);
        filters = normalize(filters);
    }

    private static List<String> normalize(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        return source.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
