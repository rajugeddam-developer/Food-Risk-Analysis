package com.foodrisk.dto;

import java.time.Instant;

/**
 * Minimal DTO for system and database health status.
 * Contains zero sensitive database credentials or host details.
 */
public record HealthResponseDto(
        String status,
        String database,
        Instant timestamp
) {
}
