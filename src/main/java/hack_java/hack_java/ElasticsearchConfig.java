package hack_java.hack_java;

import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Configuration
public class ElasticsearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${elasticsearch.url}")
    private String elasticsearchUrl;

    @Value("${elasticsearch.api-key:#{null}}")
    private String elasticsearchApiKey;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchClient() {
        logger.info("Initializing Elasticsearch cloud client");

        // For Elastic Cloud, we use the API key authentication
        if (elasticsearchApiKey != null && !elasticsearchApiKey.isEmpty()) {
            return new RestHighLevelClient(
                    RestClient.builder(HttpHost.create(elasticsearchUrl))
                            .setRequestConfigCallback(requestConfigBuilder ->
                                    requestConfigBuilder
                                            .setConnectTimeout(5000)
                                            .setSocketTimeout(60000))
                            .setHttpClientConfigCallback(httpClientBuilder ->
                                    httpClientBuilder.setDefaultHeaders(
                                            Collections.singletonList(new BasicHeader("Authorization", "ApiKey " + elasticsearchApiKey))
                                    )
                            )
            );
        }
        // Fallback if no API key is provided (not recommended for production)
        else {
            logger.warn("No API key provided for Elasticsearch Cloud. This is not recommended for production use.");
            return new RestHighLevelClient(
                    RestClient.builder(HttpHost.create(elasticsearchUrl))
                            .setRequestConfigCallback(requestConfigBuilder ->
                                    requestConfigBuilder
                                            .setConnectTimeout(5000)
                                            .setSocketTimeout(60000))
            );
        }
    }
}
