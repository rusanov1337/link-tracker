package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.ScrapperProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ScrapperClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "http", matchIfMissing = true)
    public RestClient scrapperRestClient(RestClient.Builder restClientBuilder, ScrapperProperties scrapperProperties) {
        return restClientBuilder
                .baseUrl(scrapperProperties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(scrapperProperties))
                .build();
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory(ScrapperProperties scrapperProperties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(scrapperProperties.getHttp().getConnectTimeout());
        requestFactory.setReadTimeout(scrapperProperties.getHttp().getReadTimeout());
        return requestFactory;
    }
}
