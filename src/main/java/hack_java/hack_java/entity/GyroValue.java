package hack_java.hack_java.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GyroValue {
    private double x;
    private double y;
    private double z;
    private long timestamp;
}

