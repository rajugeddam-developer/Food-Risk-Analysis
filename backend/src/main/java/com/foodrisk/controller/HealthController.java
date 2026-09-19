package com.foodrisk.controller;

import com.foodrisk.dto.HealthResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/**
 * Health verification REST controller.
 *
 * Provides a minimal endpoint to verify application and database readiness
 * without exposing internal database topology, credentials, or stack traces.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<HealthResponseDto> checkHealth() {
        boolean dbConnected = false;
        try (Connection connection = dataSource.getConnection()) {
            dbConnected = connection.isValid(2);
        } catch (Exception ignored) {
            // Keep error suppressed to prevent exposing internal connection information
        }

        String dbStatus = dbConnected ? "UP" : "DOWN";
        String overallStatus = dbConnected ? "UP" : "DEGRADED";

        return ResponseEntity.ok(new HealthResponseDto(overallStatus, dbStatus, Instant.now()));
    }
}
