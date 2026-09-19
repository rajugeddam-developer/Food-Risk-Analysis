package com.foodrisk.dto;

import java.util.UUID;

/**
 * Safe public user response DTO.
 * Explicitly never returns passwordHash.
 */
public record UserResponse(
        UUID id,
        String name,
        String email
) {
}
