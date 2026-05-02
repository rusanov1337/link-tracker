package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.ValkeyClusterProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.valkey.cluster.enabled", havingValue = "true")
public class ValkeyClusterConfiguration {

    @Bean
    public RedisClusterConfiguration redisClusterConfiguration(ValkeyClusterProperties properties) {
        var nodes = nonBlankNodes(properties.getNodes());
        Assert.notEmpty(nodes, "At least one Valkey cluster node must be configured");

        var configuration = new RedisClusterConfiguration(nodes);
        if (properties.getMaxRedirects() != null) {
            configuration.setMaxRedirects(properties.getMaxRedirects());
        }
        return configuration;
    }

    private static List<String> nonBlankNodes(List<String> nodes) {
        return nodes.stream().filter(StringUtils::hasText).toList();
    }
}
