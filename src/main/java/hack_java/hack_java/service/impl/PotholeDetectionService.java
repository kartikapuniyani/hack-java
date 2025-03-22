package hack_java.hack_java.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import hack_java.hack_java.entity.AccelValue;
import hack_java.hack_java.entity.AnomalyRequest;
import hack_java.hack_java.entity.GyroValue;
import hack_java.hack_java.entity.PotholeVerificationResult;
import hack_java.hack_java.repository.LowLevelElasticsearchRepository;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PotholeDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(PotholeDetectionService.class);

    @Value("${pothole.detection.accel-threshold:4.0}")
    private double ACCEL_Z_THRESHOLD;

    @Value("${pothole.detection.accel-variance-threshold:2.5}")
    private double ACCEL_VARIANCE_THRESHOLD;

    @Value("${pothole.detection.gyro-variance-threshold:0.05}")
    private double GYRO_VARIANCE_THRESHOLD;

    @Value("${pothole.verification.minimum-reports:2}")
    private int MINIMUM_REPORTS_FOR_CONFIRMATION;

    @Value("${pothole.verification.proximity-meters:10.0}")
    private double PROXIMITY_THRESHOLD_METERS;

    @Value("${pothole.verification.repair-time-days:30}")
    private int REPAIR_TIME_THRESHOLD_DAYS;

    @Autowired
    private LowLevelElasticsearchRepository elasticsearchRepository;

    private NotificationServiceImpl notificationService;

    /**
     * Main method to process incoming pothole reports and verify them
     */
    public PotholeVerificationResult processPotholeReport(AnomalyRequest request) {
        logger.info("Processing pothole report at location: {}, {}",
                request.getLocation().getLatitude(),
                request.getLocation().getLongitude());

        // Step 1: Analyze sensor data to confirm if this is likely a pothole
        boolean isPotholeBySensorData = analyzeAccelGyroData(request.getAccelValues(), request.getGyroValues());

//        if (!isPotholeBySensorData) {
//            logger.info("Sensor data doesn't indicate a pothole. Likely false positive.");
//            return new PotholeVerificationResult(false, false, "Sensor data insufficient for pothole classification");
//        }

//         Step 2: Check historical data to see if this pothole has been reported before
        List<Map<String, Object>> previousReports = elasticsearchRepository.findNearbyPotholes(
                request.getLocation().getLatitude(),
                request.getLocation().getLongitude(),
                PROXIMITY_THRESHOLD_METERS
        );


        // Step 3: Determine if this is a new pothole, existing pothole, or fixed pothole
        if (previousReports.isEmpty()) {
            // This is the first report of this pothole
            elasticsearchRepository.savePotholeReport(request);
            return new PotholeVerificationResult(true, false, "First report of pothole at this location");
        } else {
            // Check if this pothole has been reported multiple times (verification)
            boolean isConfirmed = previousReports.size() >= MINIMUM_REPORTS_FOR_CONFIRMATION;

            // Check if reports stopped for a while and now started again (not fixed)
            long mostRecentReportTime = getMostRecentReportTime(previousReports);
            long currentTime = request.getLocation().getTimestamp();

            boolean wasFixed = checkIfWasFixed(previousReports, currentTime);

            // Update database with new report
            elasticsearchRepository.savePotholeReport(request);

            if (wasFixed) {
                return new PotholeVerificationResult(true, false,
                        "Pothole reappeared after being potentially fixed. Reopening report.");
            } else {
                return new PotholeVerificationResult(true, isConfirmed,
                        isConfirmed ? "Confirmed pothole based on multiple reports" : "Pothole reported, needs additional verification");
            }
        }
    }

    /**
     * Analyze accelerometer and gyroscope data to determine if sensor readings indicate a pothole
     */
    private boolean analyzeAccelGyroData(List<AccelValue> accelValues, List<GyroValue> gyroValues) {
        // Extract z-axis accelerometer data (vertical movement)
        double[] accelZValues = accelValues.stream()
                .mapToDouble(AccelValue::getZ)
                .toArray();

        // Calculate statistics for accelerometer data
        DescriptiveStatistics accelStats = new DescriptiveStatistics(accelZValues);
        double accelZMin = accelStats.getMin();
        double accelZMax = accelStats.getMax();
        double accelZRange = accelZMax - accelZMin;
        double accelZVariance = accelStats.getVariance();

        // Calculate statistics for gyroscope data (rotational movement)
        double[] gyroXValues = gyroValues.stream().mapToDouble(GyroValue::getX).toArray();
        double[] gyroYValues = gyroValues.stream().mapToDouble(GyroValue::getY).toArray();

        DescriptiveStatistics gyroXStats = new DescriptiveStatistics(gyroXValues);
        DescriptiveStatistics gyroYStats = new DescriptiveStatistics(gyroYValues);

        double gyroXVariance = gyroXStats.getVariance();
        double gyroYVariance = gyroYStats.getVariance();

        // Check if the pattern matches pothole characteristics
        // 1. Significant vertical acceleration change
        // 2. High variance in accelerometer data
        // 3. Some rotation as vehicle hits pothole
        boolean significantVerticalMovement = accelZRange > ACCEL_Z_THRESHOLD;
        boolean highAccelVariance = accelZVariance > ACCEL_VARIANCE_THRESHOLD;
        boolean rotationalMovement = (gyroXVariance > GYRO_VARIANCE_THRESHOLD) ||
                (gyroYVariance > GYRO_VARIANCE_THRESHOLD);

        // Log the analysis results
        logger.debug("Pothole analysis - accelZRange: {}, accelZVariance: {}, gyroXVariance: {}, gyroYVariance: {}",
                accelZRange, accelZVariance, gyroXVariance, gyroYVariance);

        // Return true if most conditions are met - this could be refined with ML models
        return significantVerticalMovement && (highAccelVariance || rotationalMovement);
    }

    /**
     * Get the most recent report time from previous reports
     */
    private long getMostRecentReportTime(List<Map<String, Object>> previousReports) {
        return previousReports.stream()
                .mapToLong(report -> Long.parseLong(report.get("timestamp").toString()))
                .max()
                .orElse(0L);
    }

    /**
     * Check if the pothole was fixed based on reporting patterns
     */
    private boolean checkIfWasFixed(List<Map<String, Object>> previousReports, long currentTime) {
        // Sort reports by timestamp
        List<Long> reportTimestamps = previousReports.stream()
                .map(report -> Long.parseLong(report.get("timestamp").toString()))
                .sorted()
                .toList();

        // If there's a large gap between reports, pothole may have been fixed and reappeared
        if (reportTimestamps.size() >= 2) {
            long mostRecentReportTime = reportTimestamps.get(reportTimestamps.size() - 1);

            // Calculate time since last report in days
            long daysSinceLastReport = (currentTime - mostRecentReportTime) / (24 * 60 * 60 * 1000);

            // If no reports for longer than threshold, may have been fixed
            return daysSinceLastReport > REPAIR_TIME_THRESHOLD_DAYS;
        }

        return false;
    }

    public void processAndNotify(List<JsonNode> dataList) throws IOException {
        if (dataList.size() > 2) {
            List<JsonNode> delhiData = new ArrayList<>();
            List<JsonNode> gurgaonData = new ArrayList<>();
            List<JsonNode> noidaData = new ArrayList<>();
            List<String> ids = new ArrayList<>();

            for (JsonNode data : dataList) {
                if("delhi".equalsIgnoreCase(data.get("city").asText())){
                    delhiData.add(data);
                } else if ("gurgaon".equalsIgnoreCase(data.get("city").asText())) {
                    gurgaonData.add(data);
                } else if ("noida".equalsIgnoreCase(data.get("city").asText())) {
                    noidaData.add(data);
                }
                for(JsonNode delhi : delhiData){
                    notificationService.sendSms(delhi.get("address").asText());
                    ids.add(delhi.get("id").asText());
                }
                for(JsonNode noida : noidaData){
                    notificationService.sendWhatsAppSms(noida.get("address").asText());
                    ids.add(noida.get("id").asText());
                }
                for(JsonNode gurgaon : gurgaonData){
                    notificationService.sendSms(gurgaon.get("address").asText());
                    ids.add(gurgaon.get("id").asText());
                }
            }
            updateNotifyStatus(ids);
        }
    }

    private void updateNotifyStatus(List<String> ids) throws IOException {
        long timestamp = System.currentTimeMillis();
        elasticsearchRepository.update(timestamp, ids);
    }

    public void getAndUpdate() throws IOException {
        long currentTime = System.currentTimeMillis();
        long calculatedTime = currentTime - (30L * 24 * 60 * 60 * 1000);

        List<JsonNode> finalList = new ArrayList<>();

        //get all the data past 30 days
        List<JsonNode> filteredData = elasticsearchRepository.getAll(calculatedTime);
        for (JsonNode data : filteredData){

            //get the potholes within 50m distance
            List<JsonNode> nodes = elasticsearchRepository.getUpdatedList(data.get("location").get("lat").asDouble(), data.get("location").get("lon").asDouble(), calculatedTime);
            finalList.addAll(nodes);
        }
        processAndNotify(finalList);
    }
}