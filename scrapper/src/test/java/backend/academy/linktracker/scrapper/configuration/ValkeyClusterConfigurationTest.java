package backend.academy.linktracker.scrapper.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.properties.ValkeyClusterProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValkeyClusterConfigurationTest {

    @Test
    void redisClusterConfigurationUsesConfiguredNodes() {
        var properties = new ValkeyClusterProperties();
        properties.setNodes(List.of("localhost:6379", "", "localhost:6380"));
        properties.setMaxRedirects(5);

        var configuration = new ValkeyClusterConfiguration().redisClusterConfiguration(properties);

        assertThat(configuration.getClusterNodes())
                .extracting(node -> node.getHost() + ":" + node.getPort())
                .containsExactlyInAnyOrder("localhost:6379", "localhost:6380");
        assertThat(configuration.getMaxRedirects()).isEqualTo(5);
    }
}
