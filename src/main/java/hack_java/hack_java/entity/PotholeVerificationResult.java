package hack_java.hack_java.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PotholeVerificationResult {
    private boolean isPothole;
    private boolean isConfirmed;
    private String message;
}
