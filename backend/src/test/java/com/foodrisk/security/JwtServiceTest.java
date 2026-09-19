package com.foodrisk.security;

import com.foodrisk.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    // 256-bit test secret (32+ bytes)
    private static final String TEST_SECRET = "food-risk-analysis-test-jwt-secret-key-32-bytes-long!";

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setExpiration(3600000L); // 1 hour
        jwtService = new JwtService(jwtProperties);
    }

    @Test
    @DisplayName("Should generate valid JWT token with user email as subject")
    void testGenerateAndValidateToken() {
        String email = "test.user@example.com";
        String token = jwtService.generateToken(email);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractSubject(token)).isEqualTo(email);
        assertThat(jwtService.isTokenValid(token)).isTrue();

        UserDetails userDetails = new User(email, "unused", Collections.emptyList());
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("Should reject token when validated against mismatched UserDetails")
    void testValidateTokenMismatchedUser() {
        String token = jwtService.generateToken("user.one@example.com");
        UserDetails mismatchedUser = new User("user.two@example.com", "unused", Collections.emptyList());

        assertThat(jwtService.isTokenValid(token, mismatchedUser)).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void testExpiredToken() {
        jwtProperties.setExpiration(-1000L); // Expired in the past
        JwtService expiredJwtService = new JwtService(jwtProperties);

        String token = expiredJwtService.generateToken("expired@example.com");
        assertThat(expiredJwtService.isTokenExpired(token)).isTrue();
        assertThat(expiredJwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed or tampered token")
    void testMalformedToken() {
        assertThat(jwtService.isTokenValid("invalid.tampered.token")).isFalse();
    }

    @Test
    @DisplayName("Should fail fast if JWT secret is missing or empty")
    void testMissingSecretThrowsException() {
        jwtProperties.setSecret("");
        JwtService unconfiguredService = new JwtService(jwtProperties);

        assertThatThrownBy(() -> unconfiguredService.generateToken("user@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is not configured");
    }
}
