package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.properties.TrackedLinksClientSideCacheProperties;
import backend.academy.linktracker.scrapper.properties.ValkeyClusterProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.support.caching.CacheAccessor;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "app.cache.tracked-links.client-side.enabled", havingValue = "true")
public class TrackedLinksClientSideCache implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackedLinksClientSideCache.class);

    private final BoundedCacheAccessor localCache;

    @Nullable
    private final RedisClient redisClient;

    @Nullable
    private final StatefulRedisConnection<String, String> connection;

    @Nullable
    private final CacheFrontend<String, String> cacheFrontend;

    public TrackedLinksClientSideCache(
            DataRedisProperties redisProperties,
            ValkeyClusterProperties clusterProperties,
            TrackedLinksClientSideCacheProperties properties) {
        this.localCache = new BoundedCacheAccessor(properties.maxSize());
        if (clusterProperties.isEnabled()) {
            this.redisClient = null;
            this.connection = null;
            this.cacheFrontend = null;
            LOGGER.warn("Tracked links client-side cache is disabled for Valkey cluster mode");
            return;
        }

        this.redisClient = RedisClient.create(redisUri(redisProperties));
        this.redisClient.setOptions(
                ClientOptions.builder().protocolVersion(ProtocolVersion.RESP3).build());
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.cacheFrontend = ClientSideCaching.enable(
                localCache, connection, new TrackingArgs().enabled(true).noloop());
    }

    @Nullable
    public String get(String key) {
        if (cacheFrontend == null) {
            return null;
        }

        try {
            return cacheFrontend.get(key);
        } catch (RedisException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "trackedLinksClientSideCacheRead")
                    .addKeyValue("cacheKey", key)
                    .setCause(exception)
                    .log("Tracked links client-side cache is unavailable");
            return null;
        }
    }

    public void put(String key, String value) {
        localCache.put(key, value);
    }

    public void evict(String key) {
        localCache.evict(key);
    }

    @Override
    public void destroy() {
        if (cacheFrontend != null) {
            cacheFrontend.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private static RedisURI redisUri(DataRedisProperties redisProperties) {
        var builder = RedisURI.Builder.redis(redisProperties.getHost(), redisProperties.getPort())
                .withDatabase(redisProperties.getDatabase())
                .withTimeout(redisProperties.getTimeout());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            builder.withPassword(redisProperties.getPassword().toCharArray());
        }
        return builder.build();
    }

    private static final class BoundedCacheAccessor implements CacheAccessor<String, String> {
        private final Map<String, String> values;

        private BoundedCacheAccessor(int maxSize) {
            this.values = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > maxSize;
                }
            });
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void evict(String key) {
            values.remove(key);
        }
    }
}
