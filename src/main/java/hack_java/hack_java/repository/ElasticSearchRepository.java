package hack_java.hack_java.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import hack_java.hack_java.entity.AnomalyRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ElasticSearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchRepository.class);

    @Value("${elasticsearch.index.pothole}")
    private String potholeIndex;

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Save a pothole report to Elasticsearch
     */
    public void savePotholeReport(AnomalyRequest request) {
        try {
            Map<String, Object> documentMap = new HashMap<>();

            // Extract key data from the request
            documentMap.put("id", UUID.randomUUID().toString());
            documentMap.put("anomalyType", request.getAnomalyType());
            documentMap.put("timestamp", request.getLocation().getTimestamp());
            documentMap.put("reportDate", System.currentTimeMillis());

            // Store location as geo_point
            Map<String, Object> location = new HashMap<>();
            location.put("lat", request.getLocation().getLatitude());
            location.put("lon", request.getLocation().getLongitude());
            documentMap.put("location", location);

            // Store accuracy and altitude
            documentMap.put("accuracy", request.getLocation().getAccuracy());
            documentMap.put("altitude", request.getLocation().getAltitude());

            // Store sensor data statistics
            Map<String, Object> sensorStats = calculateSensorStatistics(request);
            documentMap.put("sensorStats", sensorStats);

            // Create and execute the index request
            IndexRequest indexRequest = new IndexRequest(potholeIndex)
                    .id(documentMap.get("id").toString())
                    .source(documentMap);

            elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT);
            logger.info("Saved pothole report with ID: {}", documentMap.get("id"));

        } catch (IOException e) {
            logger.error("Error saving pothole report to Elasticsearch", e);
            throw new RuntimeException("Failed to save pothole data", e);
        }
    }

    /**
     * Find nearby pothole reports within a specified distance
     */
    public List<Map<String, Object>> findNearbyPotholes(double latitude, double longitude, double distanceInMeters) {
        try {
            // Create search request
            SearchRequest searchRequest = new SearchRequest(potholeIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            // Use geo_distance query to find potholes within specified radius
            searchSourceBuilder.query(
                    QueryBuilders.geoDistanceQuery("location")
                            .point(latitude, longitude)
                            .distance(distanceInMeters, DistanceUnit.METERS)
                            .geoDistance(GeoDistance.ARC)
            );

//            // Sort by timestamp to get most recent reports first
//            searchSourceBuilder.sort(SortBuilders.fieldSort("timestamp").order(SortOrder.DESC));

            // Set size to ensure we get enough results
            searchSourceBuilder.size(20);

            searchRequest.source(searchSourceBuilder);

            // Execute search
            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            // Process results
            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                results.add(hit.getSourceAsMap());
            }

            logger.info("Found {} nearby pothole reports within {} meters of {}, {}",
                    results.size(), distanceInMeters, latitude, longitude);

            return results;

        } catch (IOException e) {
            logger.error("Error searching for nearby potholes", e);
            throw new RuntimeException("Failed to search for nearby potholes", e);
        }
    }

    /**
     * Find if a pothole has been fixed (no recent reports in location)
     */
    public boolean isPotholeFixed(String potholeId, long fixedThresholdMs) {
        try {
            // Get the pothole details
            SearchRequest searchRequest = new SearchRequest(potholeIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("id", potholeId));
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            if (searchResponse.getHits().getTotalHits().value == 0) {
                return false; // Pothole not found
            }

            Map<String, Object> potholeData = searchResponse.getHits().getHits()[0].getSourceAsMap();

            // Get location
            Map<String, Object> location = (Map<String, Object>) potholeData.get("location");
            double latitude = (double) location.get("lat");
            double longitude = (double) location.get("lon");

            // Find recent reports for this pothole location
            SearchRequest recentReportsRequest = new SearchRequest(potholeIndex);
            SearchSourceBuilder recentReportsBuilder = new SearchSourceBuilder();

            long currentTime = System.currentTimeMillis();
            long fixedThresholdTime = currentTime - fixedThresholdMs;

            recentReportsBuilder.query(
                    QueryBuilders.boolQuery()
                            .must(QueryBuilders.geoDistanceQuery("location")
                                    .point(latitude, longitude)
                                    .distance(10, DistanceUnit.METERS))
                            .must(QueryBuilders.rangeQuery("timestamp")
                                    .gte(fixedThresholdTime))
            );

            recentReportsRequest.source(recentReportsBuilder);

            SearchResponse recentReportsResponse = elasticsearchClient.search(recentReportsRequest, RequestOptions.DEFAULT);

            // If no recent reports, consider it fixed
            return recentReportsResponse.getHits().getTotalHits().value == 0;

        } catch (IOException e) {
            logger.error("Error checking if pothole is fixed", e);
            throw new RuntimeException("Failed to check pothole status", e);
        }
    }

    /**
     * Calculate statistics from sensor data for storage and later analysis
     */
    private Map<String, Object> calculateSensorStatistics(AnomalyRequest request) {
        Map<String, Object> stats = new HashMap<>();

        // Calculate accelerometer statistics
        double[] accelX = request.getAccelValues().stream().mapToDouble(value -> value.getX()).toArray();
        double[] accelY = request.getAccelValues().stream().mapToDouble(value -> value.getY()).toArray();
        double[] accelZ = request.getAccelValues().stream().mapToDouble(value -> value.getZ()).toArray();

        stats.put("accelXMean", calculateMean(accelX));
        stats.put("accelYMean", calculateMean(accelY));
        stats.put("accelZMean", calculateMean(accelZ));

        stats.put("accelXStdDev", calculateStdDev(accelX));
        stats.put("accelYStdDev", calculateStdDev(accelY));
        stats.put("accelZStdDev", calculateStdDev(accelZ));

        stats.put("accelXRange", calculateRange(accelX));
        stats.put("accelYRange", calculateRange(accelY));
        stats.put("accelZRange", calculateRange(accelZ));

        // Calculate gyroscope statistics
        double[] gyroX = request.getGyroValues().stream().mapToDouble(value -> value.getX()).toArray();
        double[] gyroY = request.getGyroValues().stream().mapToDouble(value -> value.getY()).toArray();
        double[] gyroZ = request.getGyroValues().stream().mapToDouble(value -> value.getZ()).toArray();

        stats.put("gyroXMean", calculateMean(gyroX));
        stats.put("gyroYMean", calculateMean(gyroY));
        stats.put("gyroZMean", calculateMean(gyroZ));

        stats.put("gyroXStdDev", calculateStdDev(gyroX));
        stats.put("gyroYStdDev", calculateStdDev(gyroY));
        stats.put("gyroZStdDev", calculateStdDev(gyroZ));

        return stats;
    }

    // Helper methods for statistics
    private double calculateMean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return values.length > 0 ? sum / values.length : 0;
    }

    private double calculateStdDev(double[] values) {
        double mean = calculateMean(values);
        double sum = 0;
        for (double value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return values.length > 0 ? Math.sqrt(sum / values.length) : 0;
    }

    private double calculateRange(double[] values) {
        if (values.length == 0) return 0;

        double min = values[0];
        double max = values[0];

        for (double value : values) {
            if (value < min) min = value;
            if (value > max) max = value;
        }

        return max - min;
    }
}
