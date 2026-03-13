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

    private record CommandMetricKey(String command, String status) {}

    private final MeterRegistry meterRegistry;
    private final Counter updatesTotal;
    private final Counter sendFailuresTotal;
    private final Timer processingLatencyMs;
    private final Map<CommandMetricKey, Counter> commandCounters = new ConcurrentHashMap<>();

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

    public void incrementCommandsTotal(String command, String status) {
        var metricKey = new CommandMetricKey(command, status);
        commandCounters
                .computeIfAbsent(metricKey, key -> Counter.builder("commands_total")
                        .description("Total processed bot commands by command and status")
                        .tag("command", key.command())
                        .tag("status", key.status())
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
