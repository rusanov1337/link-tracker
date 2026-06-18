package backend.academy.linktracker.bot.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BotMetricsService {

    private record CommandMetricKey(String command, String status) {}

    private record CommandDurationKey(String scope, String scopeType) {}

    private final MeterRegistry meterRegistry;
    private final Counter updatesTotal;
    private final Counter telegramRequestsTotal;
    private final Counter sentNotificationsTotal;
    private final Counter sendFailuresTotal;
    private final Timer processingLatencyMs;
    private final Map<CommandMetricKey, Counter> commandCounters = new ConcurrentHashMap<>();
    private final Map<CommandMetricKey, Counter> commandRequestCounters = new ConcurrentHashMap<>();
    private final Map<CommandDurationKey, Timer> commandDurationTimers = new ConcurrentHashMap<>();

    public BotMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.updatesTotal = Counter.builder("updates_total")
                .description("Total telegram updates received")
                .register(meterRegistry);
        this.telegramRequestsTotal = Counter.builder("telegram_requests_total")
                .description("Total Telegram Bot API requests received by Bot")
                .tag("request_type", "message")
                .register(meterRegistry);
        this.sentNotificationsTotal = Counter.builder("sent_notification_total")
                .description("Total notifications sent to users")
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
        telegramRequestsTotal.increment();
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
        commandRequestCounters
                .computeIfAbsent(metricKey, key -> Counter.builder("command_requests_total")
                        .description("Total processed bot commands")
                        .tag("command", key.command())
                        .tag("status", key.status())
                        .register(meterRegistry))
                .increment();
    }

    public void incrementSentNotificationsTotal() {
        sentNotificationsTotal.increment();
    }

    public void incrementSendFailuresTotal() {
        sendFailuresTotal.increment();
    }

    public void recordProcessingLatency(long durationNanos) {
        processingLatencyMs.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCommandDuration(String scope, String scopeType, long durationNanos) {
        commandDurationTimers
                .computeIfAbsent(
                        new CommandDurationKey(scope, scopeType), key -> Timer.builder("command_duration_ms_total")
                                .description("Bot command operation duration")
                                .tag("scope", key.scope())
                                .tag("scope_type", key.scopeType())
                                .tag("exception", "none")
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
}
