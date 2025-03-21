package hack_java.hack_java.controller;

import hack_java.hack_java.service.impl.CityPotholeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Controller for retrieving pothole data filtered by city
 */
@RestController
@RequestMapping("/api/potholes/city")
public class CityPotholeController {

    private static final Logger logger = LoggerFactory.getLogger(CityPotholeController.class);

    @Autowired
    private CityPotholeService cityPotholeService;

    /**
     * Get all potholes for a specific city
     *
     * @param city The city name to filter by
     * @return List of pothole data for the specified city
     */
    @GetMapping("/{city}")
    public ResponseEntity<List<Map<String, Object>>> getPotholesByCity(@PathVariable String city) {
        logger.info("Retrieving potholes for city: {}", city);

        List<Map<String, Object>> potholes = cityPotholeService.getPotholesByCity(city);

        logger.info("Found {} potholes in city: {}", potholes.size(), city);
        return ResponseEntity.ok(potholes);
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
    @GetMapping("/{city}/nearby")
    public ResponseEntity<List<Map<String, Object>>> getPotholesByCityAndLocation(
            @PathVariable String city,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm) {

        logger.info("Retrieving potholes for city: {} within {}km of location: {}, {}",
                city, radiusKm, latitude, longitude);

        List<Map<String, Object>> potholes = cityPotholeService.getPotholesByCityAndLocation(
                city, latitude, longitude, radiusKm);

        logger.info("Found {} potholes in city: {} within specified radius", potholes.size(), city);
        return ResponseEntity.ok(potholes);
    }

    /**
     * Get a summary of potholes grouped by city
     *
     * @return Map of city names to pothole counts
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPotholeSummaryByCity() {
        logger.info("Retrieving pothole summary by city");

        Map<String, Object> summary = cityPotholeService.getPotholeSummaryByCity();

        return ResponseEntity.ok(summary);
    }
}
