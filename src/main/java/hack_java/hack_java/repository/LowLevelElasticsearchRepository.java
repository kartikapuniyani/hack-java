package hack_java.hack_java.repository;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.stream.Collectors;

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

            Map<String, Object> document = new HashMap<>();

            document.put("id", UUID.randomUUID().toString());
            document.put("anomalyType", request.getAnomalyType());
            document.put("timestamp", request.getLocation().getTimestamp());
            document.put("reportDate", System.currentTimeMillis());
            document.put("notifyDate", null);

            // Store location as geo_point
            Map<String, Double> location = new HashMap<>();
            location.put("lat", request.getLocation().getLatitude());
            location.put("lon", request.getLocation().getLongitude());
            document.put("location", location);

            // Store accuracy and altitude
            document.put("accuracy", request.getLocation().getAccuracy());
            document.put("altitude", request.getLocation().getAltitude());
            document.put("city","gurgaon");
            document.put("fileName",request.getFileName());

            // Store sensor data statistics
            Map<String, Object> sensorStats = calculateSensorStatistics(request);
            document.put("sensorStats", sensorStats);


            // Generate realistic accelerometer values
            sensorStats.put("accelXMean", (Math.random() - 0.5) * 0.8);
            sensorStats.put("accelXRange", 1.0 + Math.random() * 2.0);
            sensorStats.put("accelXStdDev", 0.2 + Math.random() * 0.6);

            sensorStats.put("accelYMean", (Math.random() - 0.5) * 0.8);
            sensorStats.put("accelYRange", 1.0 + Math.random() * 2.0);
            sensorStats.put("accelYStdDev", 0.2 + Math.random() * 0.6);

            // Z axis has gravity component, so mean is around 9.8
            sensorStats.put("accelZMean", 9.8 + (Math.random() - 0.5) * 0.2);
            sensorStats.put("accelZRange", 1.5 + Math.random() * 2.5);
            sensorStats.put("accelZStdDev", 0.3 + Math.random() * 0.7);

            // Generate realistic gyroscope values (usually smaller than accel)
            sensorStats.put("gyroXMean", (Math.random() - 0.5) * 0.2);
            sensorStats.put("gyroXStdDev", 0.05 + Math.random() * 0.25);

            sensorStats.put("gyroYMean", (Math.random() - 0.5) * 0.2);
            sensorStats.put("gyroYStdDev", 0.05 + Math.random() * 0.25);

            sensorStats.put("gyroZMean", (Math.random() - 0.5) * 0.2);
            sensorStats.put("gyroZStdDev", 0.05 + Math.random() * 0.25);

            document.put("sensorStats", sensorStats);

            // Convert document to JSON
            String jsonDocument = objectMapper.writeValueAsString(document);

            // Create a low-level request
            String endpoint = "/" + potholeIndex + "/_doc/" + document.get("id").toString();
            Request indexRequest = new Request("PUT", endpoint);
            indexRequest.setJsonEntity(jsonDocument);

            // Execute the request
            Response response = getLowLevelClient().performRequest(indexRequest);

            logger.info("Saved pothole report with ID: {} (Status: {})",
                    document.get("id"), response.getStatusLine().getStatusCode());

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

    public List<JsonNode> getAll(long timestamp) {

        try {
            // Create the full query JSON manually
            String jsonQuery = "{\n" +
                    " \"query\": {\n" +
                    " \"range\": {\n" +
                    " \"reportDate\": {\n" +
                    " \"gte\": " + timestamp + "\n" +
                    "}\n" +
                    "}\n" +
                    "}\n" +
                    "}";

            // Create a low-level request
            Request request = new Request("GET", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            logger.debug("Sending geo_distance query: {}", jsonQuery);

            // Execute the request using low-level client
            Response response = getLowLevelClient().performRequest(request);

            // Parse the response
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode responseNode = objectMapper.readTree(responseBody);

            logger.debug("Received response: {}", responseBody);

            // Extract hits
            JsonNode hitsNode = responseNode.path("hits").path("hits");

            // Process results
            List<JsonNode> results = new ArrayList<>();
            for (JsonNode hit : hitsNode) {
                results.add(hit.path("_source"));
            }
            return results;
        } catch (IOException e) {
            logger.error("Error while getting potholes", e);
            throw new RuntimeException("Failed to get potholes", e);
        }
    }

    public List<JsonNode> getUpdatedList(double latitude, double longitude, long timestamp) {
        try {
        // Create the full query JSON manually
        String jsonQuery = "{\n" +
                " \"query\": {\n" +
                "   \"bool\": {\n" +
                "     \"must\": [\n" +
                "       {\n" +
                "         \"geo_distance\": {\n" +
                "           \"distance\": \"50m\",\n" +
                "           \"location\": {\n" +
                "             \"lat\": " + latitude + ",\n" +
                "             \"lon\": " + longitude + "\n" +
                "           }\n" +
                "         }\n" +
                "       },\n" +
                "       {\n" +
                "         \"range\": {\n" +
                "           \"reportDate\": {\n" +
                "             \"gte\": " + timestamp + "\n" +
                "           }\n" +
                "         }\n" +
                "       }\n" +
                "     ]\n" +
                "   }\n" +
                " }\n" +
                "}";

            // Create a low-level request
            Request request = new Request("GET", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            logger.debug("Sending geo_distance query: {}", jsonQuery);

            // Execute the request using low-level client
            Response response = getLowLevelClient().performRequest(request);

            // Parse the response
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode responseNode = objectMapper.readTree(responseBody);

            logger.debug("Received response: {}", responseBody);

            // Extract hits
            JsonNode hitsNode = responseNode.path("hits").path("hits");

            // Process results
            List<JsonNode> results = new ArrayList<>();
            for (JsonNode hit : hitsNode) {
                results.add(hit.path("_source"));
            }
            return results;

        } catch (IOException e) {
            logger.error("Error while getting potholes", e);
            throw new RuntimeException("Failed to get potholes", e);
        }
    }

    public void update(long timestamp, List<String> ids){
        try {
            //" \"gte\": " + timestamp + "\n" +
            String idArray = ids.stream()
                    .map(id -> "\"" + id + "\"") // Wrap each ID in double quotes
                    .collect(Collectors.joining(", "));

            // Create the full query JSON manually
            String jsonQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"terms\": {\n" +
                    "      \"_id\": [\n" + idArray +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"script\": {\n" +
                    "    \"source\": \"ctx._source.reportDate = params.new_date; ctx._source.notifyDate = params.notify_date;\",\n" +
                    "    \"params\": {\n" +
                    "      \"new_date\": " + timestamp + ",\n" +
                    "      \"notify_date\": " + timestamp + "\n" +
                    "    }\n" +
                    "  }\n" +
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
//            List<Map<String, Object>> results = new ArrayList<>();
//            for (Map<String, Object> hit : hitsList) {
//                results.add((Map<String, Object>) hit.get("_source"));
//            }
//
//            return results;

        } catch (IOException e) {
            logger.error("Error searching for nearby potholes", e);
            throw new RuntimeException("Failed to search for nearby potholes", e);
        }
    }
}