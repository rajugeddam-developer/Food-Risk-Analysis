package com.foodrisk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.LoginRequest;
import com.foodrisk.dto.RegisterRequest;
import com.foodrisk.entity.User;
import com.foodrisk.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /api/auth/register should register new user and hash password via BCrypt")
    void testValidRegistration() throws Exception {
        String uniqueEmail = "karna." + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest("Karna", uniqueEmail, "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Karna"))
                .andExpect(jsonPath("$.user.email").value(uniqueEmail))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        // Verify password in database is stored as a BCrypt hash
        Optional<User> savedUser = userRepository.findByEmail(uniqueEmail);
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getPasswordHash())
                .isNotEqualTo("securePassword123")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("securePassword123", savedUser.get().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/register with existing email should return 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        String email = "duplicate." + UUID.randomUUID() + "@example.com";
        User existingUser = new User("Existing User", email, passwordEncoder.encode("existingPass123"));
        userRepository.saveAndFlush(existingUser);

        RegisterRequest duplicateRequest = new RegisterRequest("Duplicate User", email, "newPassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    @DisplayName("POST /api/auth/register with validation failures should return 400 Bad Request")
    void testRegisterValidationFailures() throws Exception {
        // Blank name, invalid email, password under 8 characters
        RegisterRequest invalidRequest = new RegisterRequest("   ", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials should return 200 OK and JWT access token")
    void testValidLogin() throws Exception {
        String email = "login.test." + UUID.randomUUID() + "@example.com";
        String rawPassword = "validPassword123";
        User user = new User("Login User", email, passwordEncoder.encode(rawPassword));
        userRepository.saveAndFlush(user);

        LoginRequest loginRequest = new LoginRequest(email, rawPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value("Login User"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login with incorrect password should return 401 Unauthorized")
    void testLoginWithWrongPassword() throws Exception {
        String email = "wrongpass." + UUID.randomUUID() + "@example.com";
        User user = new User("User", email, passwordEncoder.encode("correctPassword123"));
        userRepository.saveAndFlush(user);

        LoginRequest loginRequest = new LoginRequest(email, "incorrectPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("POST /api/auth/login with unknown email should return 401 Unauthorized")
    void testLoginWithUnknownEmail() throws Exception {
        LoginRequest loginRequest = new LoginRequest("unknown.email@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }
}
