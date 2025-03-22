package hack_java.hack_java.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.Json;
import hack_java.hack_java.config.ApplicationConfig;
import hack_java.hack_java.dto.VoiceBotDto;
import hack_java.hack_java.dto.WhatsAppRequestDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class NotificationServiceImpl {

    private final RestClient restClient;

    private final ApplicationConfig.TwilioConfig twilioConfig;

    private final ApplicationConfig.GupShupConfig gupShupConfig;

    private final ApplicationConfig.PwdConfig config;

    public String sendSms(String address) {
        String url = String.format(twilioConfig.getSmsUrl(), twilioConfig.getAccountSid());


        String msg = "In this " + address + " find the pot hole.";

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

    public String sendWhatsAppSms(String address) {

        String msg = "In this " + address + " find the pot hole.";
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

    public String sendCAllByVoiceBot(VoiceBotDto voiceBotDto) throws JsonProcessingException {
        // Construct the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("eventName", "5xSdaLRgxb6uB6WA9wFL");
        requestBody.put("phone", "8384051766");
        Map<String,String> dynamicVariables = new HashMap<>();
        dynamicVariables.put("incident","pot hole");
        dynamicVariables.put("address", findAddress(voiceBotDto.getLongitude().toString(), voiceBotDto.getLatitude().toString()));
        dynamicVariables.put("lat", voiceBotDto.getLatitude().toString());
        dynamicVariables.put("long", voiceBotDto.getLongitude().toString());
        dynamicVariables.put("dateNtime", "22 Mar, 2025 Saturday");
        requestBody.put("dynamic_variables", dynamicVariables);

        // Make the HTTP POST request
        return restClient.post()
                .uri("https://talk-track-flow.qac24svc.dev/api/v1/agent/event-record")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }


    public String findAddress(String longitude, String latitude) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String response = restClient.get()
                .uri("https://maps.googleapis.com/maps/api/geocode/json?latlng="+longitude+","+latitude+"&key=AIzaSyDUopwrv2x_aPeOuNo6f-aWswmpPnkn6NI")
                .retrieve()
                .body(String.class);

        JsonNode jsonNode = objectMapper.readTree(response);
        List<JsonNode> results = jsonNode.findValues("results");
        JsonNode addressNode = results.get(0);
        JsonNode address_component = addressNode.get(0);
        String address = address_component.get("formatted_address").asText();
        return address;
    }
}