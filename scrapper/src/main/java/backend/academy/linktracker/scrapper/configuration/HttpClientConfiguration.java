package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.BotProperties;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

    @Bean
    public RestClient githubRestClient(RestClient.Builder restClientBuilder, GithubProperties githubProperties) {
        return restClientBuilder
                .baseUrl(githubProperties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(
                        githubProperties.getConnectTimeout(), githubProperties.getReadTimeout()))
                .build();
    }

    @Bean
    public RestClient stackoverflowRestClient(
            RestClient.Builder restClientBuilder, StackoverflowProperties stackoverflowProperties) {
        return restClientBuilder
                .baseUrl(stackoverflowProperties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(
                        stackoverflowProperties.getConnectTimeout(), stackoverflowProperties.getReadTimeout()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "http", matchIfMissing = true)
    public RestClient botRestClient(RestClient.Builder restClientBuilder, BotProperties botProperties) {
        return restClientBuilder
                .baseUrl(botProperties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(
                        botProperties.getHttp().getConnectTimeout(),
                        botProperties.getHttp().getReadTimeout()))
                .build();
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory(
            java.time.Duration connectTimeout, java.time.Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
