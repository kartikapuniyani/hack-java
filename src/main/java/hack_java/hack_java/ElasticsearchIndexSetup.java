package hack_java.hack_java;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class ElasticsearchIndexSetup {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexSetup.class);

    @Value("${elasticsearch.index.pothole}")
    private String potholeIndex;

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    /**
     * Initialize the Elasticsearch index with proper mappings
     * This runs after the application is fully started
     */
    @EventListener(ApplicationReadyEvent.class)
    public void setupElasticsearchIndex() {
        try {
            // Check if index already exists
            boolean indexExists = elasticsearchClient.indices()
                    .exists(new GetIndexRequest(potholeIndex), RequestOptions.DEFAULT);

            if (!indexExists) {
                logger.info("Creating pothole index with geo mapping...");
                createPotholeIndex();
            } else {
                logger.info("Pothole index already exists");
            }
        } catch (IOException e) {
            logger.error("Error checking or creating Elasticsearch index", e);
        }
    }

    /**
     * Create the pothole index with proper mappings
     */
    private void createPotholeIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(potholeIndex);

        // Create mapping with proper geo_point type for location
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder();
        mappingBuilder.startObject();
        {
            mappingBuilder.startObject("mappings");
            {
                mappingBuilder.startObject("properties");
                {
                    // ID field
                    mappingBuilder.startObject("id");
                    mappingBuilder.field("type", "keyword");
                    mappingBuilder.endObject();

                    // Anomaly type
                    mappingBuilder.startObject("anomalyType");
                    mappingBuilder.field("type", "keyword");
                    mappingBuilder.endObject();

                    // Timestamps
                    mappingBuilder.startObject("timestamp");
                    mappingBuilder.field("type", "date");
                    mappingBuilder.endObject();

                    mappingBuilder.startObject("reportDate");
                    mappingBuilder.field("type", "date");
                    mappingBuilder.endObject();

                    // Location as geo_point
                    mappingBuilder.startObject("location");
                    mappingBuilder.field("type", "geo_point");
                    mappingBuilder.endObject();

                    // Accuracy and altitude
                    mappingBuilder.startObject("accuracy");
                    mappingBuilder.field("type", "float");
                    mappingBuilder.endObject();

                    mappingBuilder.startObject("altitude");
                    mappingBuilder.field("type", "float");
                    mappingBuilder.endObject();

                    // Sensor statistics as nested object
                    mappingBuilder.startObject("sensorStats");
                    {
                        mappingBuilder.startObject("properties");
                        {
                            // Accelerometer stats
                            mappingBuilder.startObject("accelXMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelYMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelZMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelXStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelYStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelZStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelXRange");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelYRange");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("accelZRange");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            // Gyroscope stats
                            mappingBuilder.startObject("gyroXMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("gyroYMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("gyroZMean");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("gyroXStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("gyroYStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();

                            mappingBuilder.startObject("gyroZStdDev");
                            mappingBuilder.field("type", "float");
                            mappingBuilder.endObject();
                        }
                        mappingBuilder.endObject(); // End of properties
                    }
                    mappingBuilder.endObject(); // End of sensorStats
                }
                mappingBuilder.endObject(); // End of properties
            }
            mappingBuilder.endObject(); // End of mappings
        }
        mappingBuilder.endObject();

        // Set the mapping
        request.source(mappingBuilder);

        // Create the index
        elasticsearchClient.indices().create(request, RequestOptions.DEFAULT);
        logger.info("Successfully created pothole index with geo mapping");
    }
}
