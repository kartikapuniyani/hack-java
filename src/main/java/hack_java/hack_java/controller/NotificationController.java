package hack_java.hack_java.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import hack_java.hack_java.dto.VoiceBotDto;
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
    public ResponseEntity<String> sendSms(@RequestParam String dto) {
        String response = notificationService.sendSms(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<String> sendWhatsappSms(@RequestParam String dto) {
        String response = notificationService.sendWhatsAppSms(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/call")
    public  ResponseEntity<String> sendVoiceCall(@RequestBody VoiceBotDto voiceBotDto) throws JsonProcessingException {
           return ResponseEntity.ok(notificationService.sendCAllByVoiceBot(voiceBotDto));
    }
}