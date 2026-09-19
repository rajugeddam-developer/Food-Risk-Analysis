package com.foodrisk;

import com.foodrisk.config.DotenvEnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Food Risk Analysis Backend.
 *
 * In Milestone M2, full PostgreSQL persistence, Flyway migrations,
 * and Spring Data JPA are enabled.
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class FoodRiskAnalysisApplication {

    public static void main(String[] args) {
        DotenvEnvironmentPostProcessor.load();
        SpringApplication.run(FoodRiskAnalysisApplication.class, args);
    }
}
