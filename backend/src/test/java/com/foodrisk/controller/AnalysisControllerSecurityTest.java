package com.foodrisk.controller;

import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.entity.User;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import com.foodrisk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    private User userA;
    private User userB;
    private FoodAnalysisSession sessionUserA;
    private FoodAnalysisSession guestSession;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setName("User Alpha");
        userA.setEmail("usera@example.com");
        userA.setPasswordHash("hashed_secret");
        userA = userRepository.save(userA);

        userB = new User();
        userB.setName("User Beta");
        userB.setEmail("userb@example.com");
        userB.setPasswordHash("hashed_secret");
        userB = userRepository.save(userB);

        sessionUserA = new FoodAnalysisSession();
        sessionUserA.setUser(userA);
        sessionUserA.setSessionToken("token-usera-12345678");
        sessionUserA.setStatus(AnalysisStatus.CREATED);
        sessionUserA.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        sessionUserA = sessionRepository.save(sessionUserA);

        guestSession = new FoodAnalysisSession();
        guestSession.setUser(null);
        guestSession.setSessionToken("token-guest-12345678");
        guestSession.setStatus(AnalysisStatus.CREATED);
        guestSession.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        guestSession = sessionRepository.save(guestSession);
    }

    @Test
    @DisplayName("Guest session can be accessed anonymously without credentials")
    void testGuestSessionAccessibleAnonymously() throws Exception {
        mockMvc.perform(get("/api/analysis/{sessionId}", guestSession.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(guestSession.getId().toString()));

        mockMvc.perform(get("/api/analysis/{sessionId}/status", guestSession.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(guestSession.getId().toString()));
    }

    @Test
    @WithMockUser(username = "usera@example.com")
    @DisplayName("User A can access their own authenticated session")
    void testOwnerCanAccessOwnSession() throws Exception {
        mockMvc.perform(get("/api/analysis/{sessionId}", sessionUserA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionUserA.getId().toString()));

        mockMvc.perform(get("/api/analysis/{sessionId}/status", sessionUserA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionUserA.getId().toString()));
    }

    @Test
    @WithMockUser(username = "userb@example.com")
    @DisplayName("User B cannot access User A's session (IDOR prevention - 403 Forbidden)")
    void testUserBCannotAccessUserASession() throws Exception {
        mockMvc.perform(get("/api/analysis/{sessionId}", sessionUserA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this analysis session."));

        mockMvc.perform(get("/api/analysis/{sessionId}/status", sessionUserA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Anonymous user cannot access User A's session (403 Forbidden)")
    void testAnonymousCannotAccessUserASession() throws Exception {
        mockMvc.perform(get("/api/analysis/{sessionId}", sessionUserA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Authentication required to access this analysis session."));
    }

    @Test
    @DisplayName("Non-existent session returns 404 Not Found")
    void testNonExistentSessionReturns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/analysis/{sessionId}", randomId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Expired session returns 410 Gone")
    void testExpiredSessionReturns410() throws Exception {
        FoodAnalysisSession expiredSession = new FoodAnalysisSession();
        expiredSession.setUser(null);
        expiredSession.setSessionToken("token-expired-12345678");
        expiredSession.setStatus(AnalysisStatus.CREATED);
        expiredSession.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        expiredSession = sessionRepository.save(expiredSession);

        mockMvc.perform(get("/api/analysis/{sessionId}", expiredSession.getId()))
                .andExpect(status().isGone());
    }
}
