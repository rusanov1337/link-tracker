package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.ScrapperProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ScrapperClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "http", matchIfMissing = true)
    public RestClient scrapperRestClient(RestClient.Builder restClientBuilder, ScrapperProperties scrapperProperties) {
        return restClientBuilder.baseUrl(scrapperProperties.getBaseUrl()).build();
    }
}
