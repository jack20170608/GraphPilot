package com.graphpilot.bootstrap.micronaut;

import io.micronaut.runtime.Micronaut;

/**
 * Micronaut entry point hosting the framework-free GraphPilot core (ADR 0004).
 */
public final class GraphPilotMicronautApplication {

    private GraphPilotMicronautApplication() {
    }

    public static void main(String[] args) {
        Micronaut.run(GraphPilotMicronautApplication.class, args);
    }
}
