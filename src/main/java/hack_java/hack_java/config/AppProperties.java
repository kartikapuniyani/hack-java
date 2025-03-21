package hack_java.hack_java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

public class AppProperties {

    @Data
    @ConfigurationProperties(prefix = "gcp-bucket-config")
    @Component
    public static class GCPBucketProperties {
        private String bucketKeyFilePath;
    }

}
