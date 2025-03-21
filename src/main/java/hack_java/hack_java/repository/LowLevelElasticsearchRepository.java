package hack_java.hack_java.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import hack_java.hack_java.entity.AnomalyRequest;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
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
public class LowLevelElasticsearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(LowLevelElasticsearchRepository.class);

    @Value("${elasticsearch.index.pothole}")
    private String potholeIndex;

    @Autowired
    private RestHighLevelClient highLevelClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get the low-level client from the high-level client
     */
    private RestClient getLowLevelClient() {
        return highLevelClient.getLowLevelClient();
    }

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
            Map<String, Double> location = new HashMap<>();
            location.put("lat", request.getLocation().getLatitude());
            location.put("lon", request.getLocation().getLongitude());
            documentMap.put("location", location);

            // Store accuracy and altitude
            documentMap.put("accuracy", request.getLocation().getAccuracy());
            documentMap.put("altitude", request.getLocation().getAltitude());

            // Store sensor data statistics
            Map<String, Object> sensorStats = calculateSensorStatistics(request);
            documentMap.put("sensorStats", sensorStats);

            // Convert document to JSON
            String jsonDocument = objectMapper.writeValueAsString(documentMap);

            // Create a low-level request
            String endpoint = "/" + potholeIndex + "/_doc/" + documentMap.get("id").toString();
            Request indexRequest = new Request("PUT", endpoint);
            indexRequest.setJsonEntity(jsonDocument);

            // Execute the request
            Response response = getLowLevelClient().performRequest(indexRequest);

            logger.info("Saved pothole report with ID: {} (Status: {})",
                    documentMap.get("id"), response.getStatusLine().getStatusCode());

        } catch (IOException e) {
            logger.error("Error saving pothole report to Elasticsearch", e);
            throw new RuntimeException("Failed to save pothole data", e);
        }
    }

    /**
     * Find nearby pothole reports within a specified distance using low-level client
     * to avoid the serverless parameter restrictions
     */
    public List<Map<String, Object>> findNearbyPotholes(double latitude, double longitude, double distanceInMeters) {
        try {
            // Create the full query JSON manually
            String jsonQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"geo_distance\": {\n" +
                    "      \"distance\": \"" + distanceInMeters + "m\",\n" +
                    "      \"location\": {\n" +
                    "        \"lat\": " + latitude + ",\n" +
                    "        \"lon\": " + longitude + "\n" +
                    "      }\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    {\n" +
                    "      \"timestamp\": {\n" +
                    "        \"order\": \"desc\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"size\": 20\n" +
                    "}";

            // Create a low-level request
            Request request = new Request("POST", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            logger.debug("Sending geo_distance query: {}", jsonQuery);

            // Execute the request using low-level client
            Response response = getLowLevelClient().performRequest(request);

            // Parse the response
            String responseBody = EntityUtils.toString(response.getEntity());
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            logger.debug("Received response: {}", responseBody);

            // Extract hits
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.get("hits");

            // Process results
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> hit : hitsList) {
                results.add((Map<String, Object>) hit.get("_source"));
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
            // Create a simple term query to find the pothole by ID
            String jsonQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"term\": {\n" +
                    "      \"id\": \"" + potholeId + "\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            // Create a low-level request
            Request request = new Request("POST", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            // Execute the request
            Response response = getLowLevelClient().performRequest(request);

            // Parse the response
            String responseBody = EntityUtils.toString(response.getEntity());
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            // Extract hits
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.get("hits");

            if (hitsList.isEmpty()) {
                return false; // Pothole not found
            }

            // Get the pothole data
            Map<String, Object> potholeData = (Map<String, Object>) hitsList.get(0).get("_source");

            // Get location
            Map<String, Object> location = (Map<String, Object>) potholeData.get("location");
            double latitude = ((Number) location.get("lat")).doubleValue();
            double longitude = ((Number) location.get("lon")).doubleValue();

            // Calculate time threshold
            long currentTime = System.currentTimeMillis();
            long fixedThresholdTime = currentTime - fixedThresholdMs;

            // Create a query to find recent reports near this location
            String recentReportsQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"must\": [\n" +
                    "        {\n" +
                    "          \"geo_distance\": {\n" +
                    "            \"distance\": \"10m\",\n" +
                    "            \"location\": {\n" +
                    "              \"lat\": " + latitude + ",\n" +
                    "              \"lon\": " + longitude + "\n" +
                    "            }\n" +
                    "          }\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"range\": {\n" +
                    "            \"timestamp\": {\n" +
                    "              \"gte\": " + fixedThresholdTime + "\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            // Create a low-level request
            Request recentRequest = new Request("POST", "/" + potholeIndex + "/_search");
            recentRequest.setJsonEntity(recentReportsQuery);

            // Execute the request
            Response recentResponse = getLowLevelClient().performRequest(recentRequest);

            // Parse the response
            String recentResponseBody = EntityUtils.toString(recentResponse.getEntity());
            Map<String, Object> recentResponseMap = objectMapper.readValue(recentResponseBody, Map.class);

            // Extract hits
            Map<String, Object> recentHits = (Map<String, Object>) recentResponseMap.get("hits");
            List<Map<String, Object>> recentHitsList = (List<Map<String, Object>>) recentHits.get("hits");

            // If no recent reports, consider it fixed
            return recentHitsList.isEmpty();

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
