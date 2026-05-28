package backend.academy.linktracker.scrapper.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

class HttpResilienceExecutorTest {

    @Test
    void retryUsesConfiguredConstantBackoff() {
        var properties = new ResilienceProperties();
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setBackoff(Duration.ofMillis(40));
        var executor = new HttpResilienceExecutor(properties);
        var attempts = new AtomicInteger();
        var attemptTimes = new ArrayList<Long>();

        var result = executor.execute("retry-backoff", () -> {
            attemptTimes.add(System.nanoTime());
            if (attempts.incrementAndGet() < 3) {
                throw retryableException();
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
        assertThat(intervals(attemptTimes)).hasSize(2).allSatisfy(interval -> assertThat(interval)
                .isGreaterThanOrEqualTo(Duration.ofMillis(35)));
    }

    @Test
    void circuitBreakerClosesAfterSuccessfulHalfOpenCalls() {
        var executor = new HttpResilienceExecutor(circuitBreakerProperties());
        var calls = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> executor.execute("half-open-close", () -> fail(calls)));
        assertThrows(RuntimeException.class, () -> executor.execute("half-open-close", () -> fail(calls)));
        assertEquals(
                CircuitBreaker.State.OPEN,
                executor.circuitBreaker("half-open-close").getState());

        assertThrows(CallNotPermittedException.class, () -> executor.execute("half-open-close", () -> "blocked"));
        assertEquals(2, calls.get());

        waitForHalfOpenState(executor, "half-open-close");

        assertEquals("ok", executor.execute("half-open-close", () -> success(calls)));
        assertEquals("ok", executor.execute("half-open-close", () -> success(calls)));
        assertEquals(
                CircuitBreaker.State.CLOSED,
                executor.circuitBreaker("half-open-close").getState());
        assertEquals(4, calls.get());
    }

    @Test
    void circuitBreakerReopensAfterFailedHalfOpenCall() {
        var executor = new HttpResilienceExecutor(circuitBreakerProperties());
        var calls = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> executor.execute("half-open-open", () -> fail(calls)));
        assertThrows(RuntimeException.class, () -> executor.execute("half-open-open", () -> fail(calls)));
        assertEquals(
                CircuitBreaker.State.OPEN,
                executor.circuitBreaker("half-open-open").getState());

        waitForHalfOpenState(executor, "half-open-open");

        assertThrows(RuntimeException.class, () -> executor.execute("half-open-open", () -> fail(calls)));
        assertThrows(RuntimeException.class, () -> executor.execute("half-open-open", () -> fail(calls)));
        assertEquals(
                CircuitBreaker.State.OPEN,
                executor.circuitBreaker("half-open-open").getState());
        assertEquals(4, calls.get());
    }

    private ResilienceProperties circuitBreakerProperties() {
        var properties = new ResilienceProperties();
        properties.getRetry().setMaxAttempts(1);
        properties.getCircuitBreaker().setSlidingWindowSize(2);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        properties.getCircuitBreaker().setFailureRateThreshold(50.0F);
        properties.getCircuitBreaker().setPermittedCallsInHalfOpenState(2);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofMillis(50));
        return properties;
    }

    private String success(AtomicInteger calls) {
        calls.incrementAndGet();
        return "ok";
    }

    private String fail(AtomicInteger calls) {
        calls.incrementAndGet();
        throw new RuntimeException("failure");
    }

    private HttpServerErrorException retryableException() {
        return new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void waitForHalfOpenState(HttpResilienceExecutor executor, String clientName) {
        await().pollDelay(Duration.ofMillis(75))
                .atMost(Duration.ofMillis(200))
                .until(() -> executor.circuitBreaker(clientName).getState() == CircuitBreaker.State.HALF_OPEN);
    }

    private List<Duration> intervals(List<Long> attemptTimes) {
        var result = new ArrayList<Duration>();
        for (int i = 1; i < attemptTimes.size(); i++) {
            result.add(Duration.ofNanos(attemptTimes.get(i) - attemptTimes.get(i - 1)));
        }
        return result;
    }
}
