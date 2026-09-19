package com.foodrisk.dto;

/**
 * Registration response payload.
 */
public record RegisterResponse(
        String message,
        UserResponse user
) {
}
