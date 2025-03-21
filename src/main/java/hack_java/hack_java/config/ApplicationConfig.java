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

    @Data
    @ConfigurationProperties(prefix = "gupshup.account")
    @Component
    public static class GupShupConfig {

        private String userId;

        private String userPassword;

        private String url;
    }

    @Data
    @ConfigurationProperties(prefix = "pwd")
    @Component
    public static class PwdConfig {

        private String toNumber;

        private String nhsiToNumber;

    }

    @Data
    @ConfigurationProperties(prefix = "pothole.detection")
    @Component
    public static class DetectionConfig {

        private double accelThreshold;

        private double accelVarianceThreshold;

        private double gyroVarianceThreshold;
    }

    @Data
    @ConfigurationProperties(prefix = "pothole.verification")
    @Component
    public static class VerificationConfig {

        private int minimumReports;

        private double proximityMeters;

        private int repairTimeDays;
    }

    @Bean
    public RestClient getRestClient(){
        return RestClient.builder().build();
    }
}