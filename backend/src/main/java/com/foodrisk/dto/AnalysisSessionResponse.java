package com.foodrisk.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload returned upon creating or querying a transient food analysis session.
 */
public record AnalysisSessionResponse(
        UUID sessionId,
        String sessionToken,
        String status,
        Instant expiresAt,
        Instant createdAt
) {}
