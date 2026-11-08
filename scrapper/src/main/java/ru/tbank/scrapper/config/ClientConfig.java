package ru.tbank.scrapper.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import ru.tbank.scrapper.client.BotClient;
import ru.tbank.scrapper.client.GitHubClient;
import ru.tbank.scrapper.client.StackOverflowClient;

@Configuration
public class ClientConfig {

    @Bean
    public GitHubClient gitHubClient(RestClient.Builder builder, ScrapperConfig config) {
        RestClient.Builder githubBuilder = builder.clone()
            .baseUrl(config.github().url())
            .requestFactory(createRequestFactory())
            .defaultHeader("User-Agent", "link-tracker")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        String token = config.github().token();
        if (token != null && !token.isBlank()) {
            githubBuilder.defaultHeader("Authorization", "Bearer " + token);
        }
        return createClient(githubBuilder.build(), GitHubClient.class);
    }

    @Bean
    public StackOverflowClient stackOverflowClient(RestClient.Builder builder, ScrapperConfig config) {
        RestClient restClient = builder.clone()
            .baseUrl(config.stackOverflow().url())
            .requestFactory(createRequestFactory())
            .build();
        return createClient(restClient, StackOverflowClient.class);
    }

    @Bean
    public BotClient botClient(RestClient.Builder builder, ScrapperConfig config) {
        RestClient restClient = builder.clone()
            .baseUrl(config.botUrl())
            .requestFactory(createRequestFactory())
            .build();
        return createClient(restClient, BotClient.class);
    }

    private <T> T createClient(RestClient restClient, Class<T> clientClass) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
        return factory.createClient(clientClass);
    }

    private JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
