package backend.academy.linktracker.bot.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BotMetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter updatesTotal;
    private final Counter sendFailuresTotal;
    private final Timer processingLatencyMs;
    private final Map<String, Counter> commandCounters = new ConcurrentHashMap<>();

    public BotMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.updatesTotal = Counter.builder("updates_total")
                .description("Total telegram updates received")
                .register(meterRegistry);
        this.sendFailuresTotal = Counter.builder("send_failures_total")
                .description("Total failed sendMessage attempts")
                .register(meterRegistry);
        this.processingLatencyMs = Timer.builder("processing_latency_ms")
                .description("Processing latency for a single telegram update")
                .register(meterRegistry);
    }

    public void incrementUpdatesTotal() {
        updatesTotal.increment();
    }

    public void incrementCommandsTotal(String type) {
        commandCounters
                .computeIfAbsent(type, key -> Counter.builder("commands_total")
                        .description("Total processed bot commands by type")
                        .tag("type", key)
                        .register(meterRegistry))
                .increment();
    }

    public void incrementSendFailuresTotal() {
        sendFailuresTotal.increment();
    }

    public void recordProcessingLatency(long durationNanos) {
        processingLatencyMs.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
