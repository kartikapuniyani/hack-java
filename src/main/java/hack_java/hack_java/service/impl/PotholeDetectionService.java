package hack_java.hack_java.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import hack_java.hack_java.config.ApplicationConfig;
import hack_java.hack_java.entity.AnomalyRequest;
import hack_java.hack_java.entity.PotholeVerificationResult;
import hack_java.hack_java.repository.LowLevelElasticsearchRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class PotholeDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(PotholeDetectionService.class);

    private ApplicationConfig.VerificationConfig verificationConfig;

    private ApplicationConfig.DetectionConfig detectionConfig;

    private LowLevelElasticsearchRepository elasticsearchRepository;

    private SignedUrlService signedUrlService;

    private NotificationServiceImpl notificationService;

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
                verificationConfig.getProximityMeters()
        );

        String url = signedUrlService.generatePreSignedUrl("test-hack24", request.getFileName(),10);

        // Step 2: Determine if this is a new pothole, existing pothole, or fixed pothole
        if (previousReports.isEmpty()) {
            // This is the first report of this pothole
            elasticsearchRepository.savePotholeReport(request);
            return new PotholeVerificationResult(true, false, "First report of pothole at this location",url);
        } else {
            // Check if this pothole has been reported multiple times (verification)
            boolean isConfirmed = previousReports.size() >= verificationConfig.getMinimumReports();

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
            return daysSinceLastReport > verificationConfig.getRepairTimeDays();
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
                if ("delhi".equalsIgnoreCase(data.get("city").asText())) {
                    delhiData.add(data);
                } else if ("gurgaon".equalsIgnoreCase(data.get("city").asText())) {
                    gurgaonData.add(data);
                } else if ("noida".equalsIgnoreCase(data.get("city").asText())) {
                    noidaData.add(data);
                }
            }
            for (JsonNode delhi : delhiData) {
                notificationService.sendSms("SAS tower");
                //notificationService.sendSms(delhi.get("address").asText());
                ids.add(delhi.get("id").asText());
            }
            for (JsonNode noida : noidaData) {
                //notificationService.sendWhatsAppSms(noida.get("address").asText());
                notificationService.sendWhatsAppSms("SAS tower");
                ids.add(noida.get("id").asText());
            }
            for (JsonNode gurgaon : gurgaonData) {
                notificationService.sendSms("SAS tower");
                //notificationService.sendSms(gurgaon.get("address").asText());
                ids.add(gurgaon.get("id").asText());
            }
            updateNotifyStatus(ids);
        }
    }

    private void updateNotifyStatus(List<String> ids) throws IOException {
        long timestamp = System.currentTimeMillis();
        elasticsearchRepository.update(timestamp, ids);
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void getAndUpdate() throws IOException {
         long currentTime = System.currentTimeMillis();
        long calculatedTime = currentTime - (30L * 24 * 60 * 60 * 1000);

        List<JsonNode> finalList = new ArrayList<>();

        //get all the data past 30 days
        List<JsonNode> filteredData = elasticsearchRepository.getAll(calculatedTime);
        for (JsonNode data : filteredData){

            //get the potholes within 50m distance
            List<JsonNode> nodes = elasticsearchRepository.getUpdatedList(data.get("location").get("lat").asDouble(), data.get("location").get("lon").asDouble(), calculatedTime);
            if(nodes.size() > 0) {
                finalList.addAll(nodes);
            }
        }
        processAndNotify(finalList);
    }
}