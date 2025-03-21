package hack_java.hack_java.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "pot-hole")
public class PotHoleEntity {
}
