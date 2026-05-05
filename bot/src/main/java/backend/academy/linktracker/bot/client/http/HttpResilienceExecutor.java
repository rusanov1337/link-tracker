package backend.academy.linktracker.bot.client.http;

import backend.academy.linktracker.bot.properties.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpResilienceExecutor {

    private final ResilienceProperties properties;
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public HttpResilienceExecutor(ResilienceProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String clientName, Supplier<T> operation) {
        var retry = retries.computeIfAbsent(clientName, this::createRetry);
        var circuitBreaker = circuitBreakers.computeIfAbsent(clientName, this::createCircuitBreaker);
        var resilientOperation =
                CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, operation));
        return resilientOperation.get();
    }

    private Retry createRetry(String clientName) {
        var retryProperties = properties.getRetry();
        var retryConfig = RetryConfig.custom()
                .maxAttempts(retryProperties.getMaxAttempts())
                .waitDuration(retryProperties.getBackoff())
                .retryOnException(this::isRetryableException)
                .build();
        return Retry.of(clientName, retryConfig);
    }

    private CircuitBreaker createCircuitBreaker(String clientName) {
        var circuitBreakerProperties = properties.getCircuitBreaker();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(circuitBreakerProperties.getSlidingWindowSize())
                .minimumNumberOfCalls(circuitBreakerProperties.getMinimumNumberOfCalls())
                .failureRateThreshold(circuitBreakerProperties.getFailureRateThreshold())
                .permittedNumberOfCallsInHalfOpenState(circuitBreakerProperties.getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(circuitBreakerProperties.getWaitDurationInOpenState())
                .recordException(exception -> !(exception instanceof CallNotPermittedException))
                .build();
        return CircuitBreaker.of(clientName, circuitBreakerConfig);
    }

    private boolean isRetryableException(Throwable exception) {
        if (!(exception instanceof RestClientResponseException responseException)) {
            return false;
        }

        return properties
                .getRetry()
                .getRetryableStatuses()
                .contains(responseException.getStatusCode().value());
    }
}
