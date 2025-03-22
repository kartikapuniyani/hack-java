package hack_java.hack_java.entity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyRequest {
    private String anomalyType;
    private List<AccelValue> accelValues;
    private List<GyroValue> gyroValues;
    private LocationData location;
    private String fileName;
}