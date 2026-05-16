package co.empresa.vivaeventos.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VivaeventosPaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VivaeventosPaymentsApplication.class, args);
    }

}
