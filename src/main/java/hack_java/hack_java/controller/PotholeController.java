package hack_java.hack_java.controller;

import hack_java.hack_java.entity.AnomalyRequest;
import hack_java.hack_java.entity.PotholeVerificationResult;
import hack_java.hack_java.service.impl.PotholeDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/potholes")
public class PotholeController {

    private static final Logger logger = LoggerFactory.getLogger(PotholeController.class);

    @Autowired
    private PotholeDetectionService potholeDetectionService;

    @PostMapping("/detect")
    public ResponseEntity<PotholeVerificationResult> detectPothole(@RequestBody AnomalyRequest request) {
        logger.info("Received pothole detection request for location: {}, {}",
                request.getLocation().getLatitude(), request.getLocation().getLongitude());

        PotholeVerificationResult result = potholeDetectionService.processPotholeReport(request);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<String> checkServiceStatus() {
        return ResponseEntity.ok("Pothole detection service is running");
    }
}
