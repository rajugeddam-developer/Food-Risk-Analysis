package com.foodrisk.dto;

/**
 * Authentication response payload containing the JWT token and user profile.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
