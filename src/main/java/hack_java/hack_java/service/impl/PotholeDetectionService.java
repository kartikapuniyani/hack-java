package hack_java.hack_java.service.impl;

import hack_java.hack_java.entity.AccelValue;
import hack_java.hack_java.entity.AnomalyRequest;
import hack_java.hack_java.entity.GyroValue;
import hack_java.hack_java.entity.PotholeVerificationResult;
import hack_java.hack_java.repository.LowLevelElasticsearchRepository;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Autowired
    private SignedUrlService signedUrlService;

    /**
     * Main method to process incoming pothole reports and verify them
     */
    public PotholeVerificationResult processPotholeReport(AnomalyRequest request) {
        logger.info("Processing pothole report at location: {}, {}",
                request.getLocation().getLatitude(),
                request.getLocation().getLongitude());


//         Step 1: Check historical data to see if this pothole has been reported before
        List<Map<String, Object>> previousReports = elasticsearchRepository.findNearbyPotholes(
                request.getLocation().getLatitude(),
                request.getLocation().getLongitude(),
                PROXIMITY_THRESHOLD_METERS
        );

        String url = signedUrlService.generateSignedUrl("test-hack24", request.getFileName(),10);

        // Step 2: Determine if this is a new pothole, existing pothole, or fixed pothole
        if (previousReports.isEmpty()) {
            // This is the first report of this pothole
            elasticsearchRepository.savePotholeReport(request);
            return new PotholeVerificationResult(true, false, "First report of pothole at this location",url);
        } else {
            // Check if this pothole has been reported multiple times (verification)
            boolean isConfirmed = previousReports.size() >= MINIMUM_REPORTS_FOR_CONFIRMATION;

            // Check if reports stopped for a while and now started again (not fixed)
            long currentTime = request.getLocation().getTimestamp();

            boolean wasFixed = checkIfWasFixed(previousReports, currentTime);

            // Update database with new report
            elasticsearchRepository.savePotholeReport(request);

            if (wasFixed) {
                return new PotholeVerificationResult(true, false,
                        "Pothole reappeared after being potentially fixed. Reopening report.",null);
            } else {
                return new PotholeVerificationResult(true, isConfirmed,
                        isConfirmed ? "Confirmed pothole based on multiple reports" : "Pothole reported, needs additional verification",url);
            }
        }
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
}