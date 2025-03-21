package hack_java.hack_java.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@Data
public class ApplicationConfig {

    @Data
    @ConfigurationProperties(prefix = "twilio.account")
    @Component
    public static class TwilioConfig {

        private String accountSid;

        private String authToken;

        private String smsUrl;

        private String fromNumber;

    }

    @Bean
    public RestClient getRestClient(){
        return RestClient.builder().build();
    }
}