package com.foodrisk.repository;

import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Rollback
class FoodAnalysisSessionRepositoryTest {

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist transient session for guest scan (no user attached)")
    void testSaveGuestSession() {
        String token = "guest-token-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

        FoodAnalysisSession session = new FoodAnalysisSession(token, AnalysisStatus.CREATED, expiresAt);
        FoodAnalysisSession saved = sessionRepository.saveAndFlush(session);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionToken()).isEqualTo(token);
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.CREATED);
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.isExpired()).isFalse();

        Optional<FoodAnalysisSession> found = sessionRepository.findBySessionToken(token);
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("Should persist transient session associated with an authenticated user")
    void testSaveSessionWithUser() {
        User user = new User("Scan User", "scanner@example.com", "hash_user");
        User savedUser = userRepository.saveAndFlush(user);

        String token = "auth-token-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        FoodAnalysisSession session = new FoodAnalysisSession(token, AnalysisStatus.PROCESSING, savedUser, expiresAt);
        FoodAnalysisSession saved = sessionRepository.saveAndFlush(session);

        assertThat(saved.getUser()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(saved.getUser().getEmail()).isEqualTo("scanner@example.com");
    }

    @Test
    @DisplayName("Should find expired sessions using findByExpiresAtBefore")
    void testFindByExpiresAtBefore() {
        Instant now = Instant.now();

        // Expired session created in the past
        String expiredToken = "expired-token-" + UUID.randomUUID();
        FoodAnalysisSession expiredSession = new FoodAnalysisSession(
                expiredToken,
                AnalysisStatus.EXPIRED,
                now.minus(10, ChronoUnit.MINUTES)
        );
        sessionRepository.saveAndFlush(expiredSession);

        // Active session expiring in the future
        String activeToken = "active-token-" + UUID.randomUUID();
        FoodAnalysisSession activeSession = new FoodAnalysisSession(
                activeToken,
                AnalysisStatus.COMPLETED,
                now.plus(1, ChronoUnit.HOURS)
        );
        sessionRepository.saveAndFlush(activeSession);

        List<FoodAnalysisSession> expiredList = sessionRepository.findByExpiresAtBefore(now);
        assertThat(expiredList)
                .extracting(FoodAnalysisSession::getSessionToken)
                .contains(expiredToken)
                .doesNotContain(activeToken);
    }

    @Test
    @DisplayName("Should find sessions by AnalysisStatus")
    void testFindByStatus() {
        String token1 = "token-created-" + UUID.randomUUID();
        sessionRepository.saveAndFlush(new FoodAnalysisSession(token1, AnalysisStatus.CREATED, Instant.now().plus(10, ChronoUnit.MINUTES)));

        String token2 = "token-failed-" + UUID.randomUUID();
        sessionRepository.saveAndFlush(new FoodAnalysisSession(token2, AnalysisStatus.FAILED, Instant.now().plus(10, ChronoUnit.MINUTES)));

        List<FoodAnalysisSession> createdSessions = sessionRepository.findByStatus(AnalysisStatus.CREATED);
        assertThat(createdSessions)
                .extracting(FoodAnalysisSession::getSessionToken)
                .contains(token1)
                .doesNotContain(token2);
    }
}
