package hack_java.hack_java.controller;

import hack_java.hack_java.service.impl.NotificationServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
@AllArgsConstructor
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    @PostMapping("/send")
    public ResponseEntity<String> sendSms(@RequestParam String to,
                                          @RequestParam String message) {
        String response = notificationService.sendSms(to, message);
        return ResponseEntity.ok(response);
    }
}