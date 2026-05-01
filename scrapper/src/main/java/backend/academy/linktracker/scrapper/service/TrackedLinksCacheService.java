package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.api.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.properties.TrackedLinksCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrackedLinksCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackedLinksCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TrackedLinksCacheProperties properties;

    public TrackedLinksCacheService(StringRedisTemplate redisTemplate, TrackedLinksCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.properties = properties;
    }

    public Optional<ListLinksResponse> get(long chatId) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        var cacheKey = cacheKey(chatId);
        try {
            var cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(cachedJson, ListLinksResponse.class));
        } catch (JsonProcessingException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksCacheRead")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("cacheKey", cacheKey)
                    .setCause(exception)
                    .log("Cached tracked links response is invalid");
            evict(chatId);
            return Optional.empty();
        } catch (DataAccessException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksCacheRead")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("cacheKey", cacheKey)
                    .setCause(exception)
                    .log("Tracked links cache is unavailable");
            return Optional.empty();
        }
    }

    public void put(long chatId, ListLinksResponse response) {
        if (!properties.enabled()) {
            return;
        }

        var cacheKey = cacheKey(chatId);
        try {
            var responseJson = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, responseJson, properties.ttl());
        } catch (JsonProcessingException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksCacheWrite")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("cacheKey", cacheKey)
                    .setCause(exception)
                    .log("Failed to serialize tracked links response");
        } catch (DataAccessException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksCacheWrite")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("cacheKey", cacheKey)
                    .setCause(exception)
                    .log("Failed to write tracked links cache");
        }
    }

    public void evict(long chatId) {
        if (!properties.enabled()) {
            return;
        }

        var cacheKey = cacheKey(chatId);
        try {
            redisTemplate.delete(cacheKey);
        } catch (DataAccessException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksCacheEvict")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("cacheKey", cacheKey)
                    .setCause(exception)
                    .log("Failed to evict tracked links cache");
        }
    }

    public String cacheKey(long chatId) {
        return properties.keyPrefix() + ":" + chatId;
    }
}
