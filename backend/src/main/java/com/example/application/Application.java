package com.example.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Main application class for the Reservation Management System.
 * This application handles insurance claim reservations and adjustments.
 * <p>
 * Converted from Oracle Forms application SINF50104.
 *
 * @version 1.0
 * @author [Your Name]
 * @see com.example.application.config.SecurityConfig Security Configuration
 * @see com.example.application.config.DatabaseConfig Database Configuration
 */
@SpringBootApplication
// Removing @EnableJpaRepositories and @EnableTransactionManagement as Spring Boot auto-configures these.
// If custom repository base packages or transaction manager is needed, uncomment and configure accordingly.
//@EnableJpaRepositories("com.example.application.repository")
//@EnableTransactionManagement
@ComponentScan(basePackages = "com.example.application") // Explicitly define the base package for component scanning
public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    /**
     * Main method to start the Spring Boot application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            SpringApplication app = new SpringApplication(Application.class);
            Environment env = app.run(args).getEnvironment();

            String protocol = "http";
            if (env.getProperty("server.ssl.key-store") != null) {
                protocol = "https";
            }
            LOGGER.info("\n----------------------------------------------------------\n\t" +
                            "Application '{}' is running! Access URLs:\n\t" +
                            "Local: \t\t{}://localhost:{}\n\t" +
                            "External: \t{}://{}:{}\n\t" +
                            "Profile(s): \t{}\n----------------------------------------------------------",
                    env.getProperty("spring.application.name"),
                    protocol,
                    env.getProperty("server.port"),
                    protocol,
                    env.getProperty("server.address"),
                    env.getProperty("server.port"),
                    env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : Arrays.asList(env.getActiveProfiles())
            );

        } catch (Exception ex) {
            LOGGER.error("Application failed to start", ex);
        }
    }
}