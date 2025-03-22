package hack_java.hack_java;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HackJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(HackJavaApplication.class, args);
	}

}
