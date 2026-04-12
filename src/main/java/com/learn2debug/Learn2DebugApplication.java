package com.learn2debug;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Learn2DebugApplication {

    public static void main(String[] args) {
        SpringApplication.run(Learn2DebugApplication.class, args);
    }
}
