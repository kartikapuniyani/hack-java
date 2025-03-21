package hack_java.hack_java.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for retrieving and analyzing pothole data by city
 */
@Service
public class CityPotholeService {

    private static final Logger logger = LoggerFactory.getLogger(CityPotholeService.class);

    @Value("${elasticsearch.index.pothole}")
    private String potholeIndex;

    @Autowired
    private RestHighLevelClient highLevelClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignedUrlService signedUrlService;

    /**
     * Get all potholes for a specific city
     *
     * @param city The city name to filter by
     * @return List of pothole data for the specified city
     */
    public List<Map<String, Object>> getPotholesByCity(String city) {
        try {
            RestClient lowLevelClient = highLevelClient.getLowLevelClient();

            // Create query for filtering by city
            String jsonQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"match\": {\n" +
                    "      \"city\": \"" + city + "\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    {\n" +
                    "      \"reportDate\": {\n" +
                    "        \"order\": \"desc\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"size\": 1000\n" +
                    "}";

            // Create request
            Request request = new Request("POST", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            // Execute request
            Response response = lowLevelClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.get("hits");

            // Extract source data
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source.containsKey("fileName")) {
                    // Extract fileName from videoUrl if present
                    String fileName = (String) source.get("fileName");
                    String url = signedUrlService.generateSignedUrl("test-hack24", fileName,60);
                    source.put("signedUrl", url);
                }
                results.add(source);
            }

            return results;

        } catch (IOException e) {
            logger.error("Error retrieving potholes by city", e);
            throw new RuntimeException("Failed to retrieve potholes by city: " + city, e);
        }
    }

    /**
     * Get potholes for a city within a specific radius of a location
     *
     * @param city The city name to filter by
     * @param latitude The latitude of the center point
     * @param longitude The longitude of the center point
     * @param radiusKm The radius in kilometers to search within
     * @return List of pothole data matching the criteria
     */
    public List<Map<String, Object>> getPotholesByCityAndLocation(
            String city, double latitude, double longitude, double radiusKm) {

        try {
            RestClient lowLevelClient = highLevelClient.getLowLevelClient();

            // Create query that combines city filter and geo_distance
            String jsonQuery = "{\n" +
                    "  \"query\": {\n" +
                    "    \"bool\": {\n" +
                    "      \"filter\": [\n" +
                    "        { \"match\": { \"city\": \"" + city + "\" } },\n" +
                    "        {\n" +
                    "          \"geo_distance\": {\n" +
                    "            \"distance\": \"" + radiusKm + "km\",\n" +
                    "            \"location\": {\n" +
                    "              \"lat\": " + latitude + ",\n" +
                    "              \"lon\": " + longitude + "\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"sort\": [\n" +
                    "    {\n" +
                    "      \"_geo_distance\": {\n" +
                    "        \"location\": {\n" +
                    "          \"lat\": " + latitude + ",\n" +
                    "          \"lon\": " + longitude + "\n" +
                    "        },\n" +
                    "        \"order\": \"asc\",\n" +
                    "        \"unit\": \"km\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"size\": 1000\n" +
                    "}";

            // Create request
            Request request = new Request("POST", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            // Execute request
            Response response = lowLevelClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.get("hits");

            // Extract source data
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> hit : hitsList) {
                results.add((Map<String, Object>) hit.get("_source"));
            }

            return results;

        } catch (IOException e) {
            logger.error("Error retrieving potholes by city and location", e);
            throw new RuntimeException("Failed to retrieve potholes by city and location", e);
        }
    }

    /**
     * Get a summary of potholes grouped by city
     *
     * @return Map of city names to pothole counts and statistics
     */
    public Map<String, Object> getPotholeSummaryByCity() {
        try {
            RestClient lowLevelClient = highLevelClient.getLowLevelClient();

            // Create aggregation query to group by city
            String jsonQuery = "{\n" +
                    "  \"size\": 0,\n" +
                    "  \"aggs\": {\n" +
                    "    \"by_city\": {\n" +
                    "      \"terms\": {\n" +
                    "        \"field\": \"city.keyword\",\n" +
                    "        \"size\": 100\n" +
                    "      },\n" +
                    "      \"aggs\": {\n" +
                    "        \"confirmed_count\": {\n" +
                    "          \"filter\": {\n" +
                    "            \"term\": {\n" +
                    "              \"isConfirmed\": true\n" +
                    "            }\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"latest_report\": {\n" +
                    "          \"max\": {\n" +
                    "            \"field\": \"reportDate\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"avg_severity\": {\n" +
                    "          \"avg\": {\n" +
                    "            \"field\": \"sensorStats.accelZRange\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            // Create request
            Request request = new Request("POST", "/" + potholeIndex + "/_search");
            request.setJsonEntity(jsonQuery);

            // Execute request
            Response response = lowLevelClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> aggregations = (Map<String, Object>) responseMap.get("aggregations");
            Map<String, Object> byCity = (Map<String, Object>) aggregations.get("by_city");
            List<Map<String, Object>> buckets = (List<Map<String, Object>>) byCity.get("buckets");

            // Process results
            Map<String, Object> summary = new HashMap<>();
            List<Map<String, Object>> citySummaries = new ArrayList<>();

            for (Map<String, Object> bucket : buckets) {
                String city = (String) bucket.get("key");
                int totalCount = ((Number) bucket.get("doc_count")).intValue();

                Map<String, Object> confirmedCount = (Map<String, Object>) bucket.get("confirmed_count");
                int confirmed = ((Number) confirmedCount.get("doc_count")).intValue();

                Map<String, Object> latestReport = (Map<String, Object>) bucket.get("latest_report");
                long latestDate = latestReport.get("value") != null ?
                        ((Number) latestReport.get("value")).longValue() : 0;

                Map<String, Object> avgSeverity = (Map<String, Object>) bucket.get("avg_severity");
                double severity = avgSeverity.get("value") != null ?
                        ((Number) avgSeverity.get("value")).doubleValue() : 0.0;

                Map<String, Object> citySummary = new HashMap<>();
                citySummary.put("city", city);
                citySummary.put("totalPotholes", totalCount);
                citySummary.put("confirmedPotholes", confirmed);
                citySummary.put("latestReportDate", latestDate);
                citySummary.put("averageSeverity", Math.round(severity * 100.0) / 100.0);

                citySummaries.add(citySummary);
            }

            summary.put("cities", citySummaries);
            summary.put("totalCities", citySummaries.size());

            return summary;

        } catch (IOException e) {
            logger.error("Error retrieving pothole summary by city", e);
            throw new RuntimeException("Failed to retrieve pothole summary by city", e);
        }
    }
}
