package org.institutonata.suprihub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupriHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupriHubApplication.class, args);
    }
}
