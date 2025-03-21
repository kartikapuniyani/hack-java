package hack_java.hack_java.service.impl;

import hack_java.hack_java.config.ApplicationConfig;
import hack_java.hack_java.dto.WhatsAppRequestDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    private ApplicationConfig.GupShupConfig gupShupConfig;

    private ApplicationConfig.PwdConfig config;

    public String sendSms(WhatsAppRequestDTO dto) {
        String url = String.format(twilioConfig.getSmsUrl(), twilioConfig.getAccountSid());


        String msg = "In this " + dto.getLatitude() + " " + dto.getLongitude() + "find the pot hole.";

        // Encode data for x-www-form-urlencoded
        String requestBody = "To=" + URLEncoder.encode("+" + config.getNhsiToNumber(), StandardCharsets.UTF_8) +
                "&From=" + URLEncoder.encode("+" + twilioConfig.getFromNumber(), StandardCharsets.UTF_8) +
                "&Body=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);

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

    public String sendWhatsAppSms(WhatsAppRequestDTO request) {

        String msg = "In this " + "location " + "find the pot hole.";
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("v", "1.1");
        formData.add("method", "SendMessage");
        formData.add("auth_scheme", "plain");
        formData.add("format", "json");
        formData.add("isTemplate", "false");
        formData.add("msg_type", "TEXT");
        formData.add("send_to", config.getToNumber());
        formData.add("userid", gupShupConfig.getUserId());
        formData.add("password", gupShupConfig.getUserPassword());
        formData.add("msg", msg);

        return restClient.post()
                .uri(gupShupConfig.getUrl())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(formData)
                .retrieve()
                .body(String.class);
    }
}