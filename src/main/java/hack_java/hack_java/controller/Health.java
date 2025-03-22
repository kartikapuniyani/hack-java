package hack_java.hack_java.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author vineet.rajput
 * description: class contain the health based urls
 */
@RestController
public class Health{

    /**
     * Health API
     * @return
     */
    @GetMapping(value = "/public/health")
    public ResponseEntity<String> health(){
        return new ResponseEntity<>("Ok health", HttpStatus.OK);
    }

}