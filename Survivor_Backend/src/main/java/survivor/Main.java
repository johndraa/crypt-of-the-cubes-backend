package survivor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author John Draa
 */

@SpringBootApplication
@EnableScheduling
public class Main
{
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
