package org.ikigaidigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Component scanning wires the HTTP adapter, application service,
 * and repository, and auto-configuration initializes the data source and Flyway migrations.
 */
@SpringBootApplication
public class TimeDepositApplication {
    /**
     * Starts the application context and embedded HTTP server with the default web configuration.
     *
     * @param args command-line options, including Spring configuration overrides
     */
    public static void main(String[] args) {
        SpringApplication.run(TimeDepositApplication.class, args);
    }
}
