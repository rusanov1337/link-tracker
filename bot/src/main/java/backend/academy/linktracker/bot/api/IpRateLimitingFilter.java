package backend.academy.linktracker.bot.api;

import backend.academy.linktracker.bot.properties.RateLimitingProperties;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(prefix = "app.rate-limiting", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class IpRateLimitingFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String FORWARDED_FOR_SEPARATOR = ",";

    private final RateLimiterConfig rateLimiterConfig;
    private final Cache<String, RateLimiter> rateLimiters;

    public IpRateLimitingFilter(RateLimitingProperties properties) {
        this.rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.limitForPeriod())
                .limitRefreshPeriod(properties.limitRefreshPeriod())
                .timeoutDuration(properties.timeoutDuration())
                .build();
        this.rateLimiters = CacheBuilder.newBuilder()
                .maximumSize(properties.cacheMaximumSize())
                .expireAfterAccess(properties.cacheExpireAfterAccess())
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var clientIp = resolveClientIp(request);
        var rateLimiter = rateLimiter(clientIp);
        if (!rateLimiter.acquirePermission()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimiter rateLimiter(String clientIp) {
        try {
            return rateLimiters.get(clientIp, () -> createRateLimiter(clientIp));
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to create rate limiter for client IP", exception);
        }
    }

    private RateLimiter createRateLimiter(String clientIp) {
        return RateLimiter.of("http-" + clientIp, rateLimiterConfig);
    }

    private String resolveClientIp(HttpServletRequest request) {
        var forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(FORWARDED_FOR_SEPARATOR, 2)[0].strip();
        }

        return request.getRemoteAddr();
    }
}
