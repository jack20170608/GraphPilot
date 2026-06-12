package com.graphpilot.bootstrap.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.graphpilot")
public class GraphPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphPilotApplication.class, args);
    }
}
