package com.graphpilot.bootstrap.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone worker process bootstrap.
 * Listens on port 8081 by default and executes tasks dispatched by the scheduler.
 */
@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}