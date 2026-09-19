package com.foodrisk.dto;

import com.foodrisk.entity.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * M11/M12 Public status response for analysis progress tracking.
 */
public record AnalysisStatusResponse(
        UUID sessionId,
        AnalysisStatus status,
        String currentStage,
        String errorMessage,
        Instant expiresAt
) {
}
