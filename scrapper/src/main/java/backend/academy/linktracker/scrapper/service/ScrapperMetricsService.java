package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ScrapperMetricsService {

    private static final String GITHUB_SOURCE = "github";
    private static final String STACKOVERFLOW_SOURCE = "stackoverflow";
    private static final String UNKNOWN_SOURCE = "unknown";

    private record ApiRequestKey(String source) {}

    private record DurationKey(String scope, String scopeType) {}

    private final MeterRegistry meterRegistry;
    private final Map<ApiRequestKey, Counter> apiRequestCounters = new ConcurrentHashMap<>();
    private final Map<DurationKey, Timer> durationTimers = new ConcurrentHashMap<>();

    public ScrapperMetricsService(MeterRegistry meterRegistry, TrackedLinkRepository trackedLinkRepository) {
        this.meterRegistry = meterRegistry;
        registerTrackedLinksGauge(trackedLinkRepository, GITHUB_SOURCE);
        registerTrackedLinksGauge(trackedLinkRepository, STACKOVERFLOW_SOURCE);
    }

    public void incrementApiRequests(String source) {
        apiRequestCounters
                .computeIfAbsent(new ApiRequestKey(source), key -> Counter.builder("api_requests_total")
                        .description("Total Scrapper API requests by source")
                        .tag("source", key.source())
                        .register(meterRegistry))
                .increment();
    }

    public void recordRequestDuration(String scope, String scopeType, long durationNanos) {
        durationTimers
                .computeIfAbsent(new DurationKey(scope, scopeType), key -> Timer.builder("request_duration_ms_total")
                        .description("Scrapper operation duration")
                        .tag("scope", key.scope())
                        .tag("scope_type", key.scopeType())
                        .serviceLevelObjectives(
                                Duration.ofMillis(25),
                                Duration.ofMillis(50),
                                Duration.ofMillis(100),
                                Duration.ofMillis(250),
                                Duration.ofMillis(500),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(5))
                        .publishPercentileHistogram()
                        .register(meterRegistry))
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public static String trackedSource(URI uri) {
        var host = uri.getHost();
        if (host == null) {
            return UNKNOWN_SOURCE;
        }
        if (host.equalsIgnoreCase("github.com")) {
            return GITHUB_SOURCE;
        }
        if (host.equalsIgnoreCase("stackoverflow.com")) {
            return STACKOVERFLOW_SOURCE;
        }
        return UNKNOWN_SOURCE;
    }

    private void registerTrackedLinksGauge(TrackedLinkRepository trackedLinkRepository, String source) {
        meterRegistry.gauge(
                "links_on_track_total",
                io.micrometer.core.instrument.Tags.of("tracked_source", source),
                trackedLinkRepository,
                repository -> repository.findAll().stream()
                        .filter(link -> source.equals(ScrapperMetricsService.trackedSource(link.url())))
                        .count());
    }
}
