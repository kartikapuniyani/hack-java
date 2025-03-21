package hack_java.hack_java.service.impl;

import hack_java.hack_java.config.ApplicationConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
@AllArgsConstructor
public class NotificationServiceImpl {

    private RestClient restClient;

    private ApplicationConfig.TwilioConfig twilioConfig;


    public String sendSms(String to, String message) {
        String url = String.format(twilioConfig.getSmsUrl(), twilioConfig.getAccountSid());

        // Encode data for x-www-form-urlencoded
        String requestBody = "To=" + URLEncoder.encode("+" + to, StandardCharsets.UTF_8) +
                "&From=" + URLEncoder.encode("+" + twilioConfig.getFromNumber(), StandardCharsets.UTF_8) +
                "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        // Generate Basic Auth Header
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((twilioConfig.getAccountSid() + ":" + twilioConfig.getAuthToken()).getBytes());

        // Call Twilio API
        return restClient.post()
                .uri(url)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }
}