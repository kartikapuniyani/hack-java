package hack_java.hack_java.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsAppRequestDTO {

    private double latitude;

    private double longitude;

    private String address;
}