package hack_java.hack_java.controller;

import hack_java.hack_java.dto.WhatsAppRequestDTO;
import hack_java.hack_java.service.impl.NotificationServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
@AllArgsConstructor
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    @PostMapping("/send")
    public ResponseEntity<String> sendSms(@RequestParam String address) {
        String response = notificationService.sendSms(address);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> sendWhatsappSms(@RequestParam String address) {
        String response = notificationService.sendWhatsAppSms(address);
        return ResponseEntity.ok(response);
    }
}