package hack_java.hack_java.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {
    private double latitude;
    private double longitude;
    private double altitude;
    private double accuracy;
    private long timestamp;
}

