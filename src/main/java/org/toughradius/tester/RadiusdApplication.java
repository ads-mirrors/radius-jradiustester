package org.toughradius.tester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class RadiusdApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(RadiusdApplication.class, args);
    }
}