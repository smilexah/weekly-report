package ai.lab.weeklyreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WeeklyreportApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeeklyreportApplication.class, args);
    }

}
