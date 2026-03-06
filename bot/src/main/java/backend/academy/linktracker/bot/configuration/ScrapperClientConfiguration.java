package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.ScrapperProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ScrapperClientConfiguration {

    @Bean
    public RestClient scrapperRestClient(RestClient.Builder restClientBuilder, ScrapperProperties scrapperProperties) {
        return restClientBuilder.baseUrl(scrapperProperties.getBaseUrl()).build();
    }
}
