package backend.academy.linktracker.scrapper.repository.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class SubscriptionRepositorySupport {

    private SubscriptionRepositorySupport() {}

    public static void validatePage(int limit, int offset) {
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Page offset must be non-negative");
        }
    }

    public static <K, V> Map<K, List<String>> groupValues(
            List<V> values, Function<V, K> keyExtractor, Function<V, String> valueExtractor) {
        var valuesBySubscription = new HashMap<K, List<String>>();
        for (var value : values) {
            valuesBySubscription
                    .computeIfAbsent(keyExtractor.apply(value), ignored -> new ArrayList<>())
                    .add(valueExtractor.apply(value));
        }
        return Map.copyOf(valuesBySubscription);
    }
}
