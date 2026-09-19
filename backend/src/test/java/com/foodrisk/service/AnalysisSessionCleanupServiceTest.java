package com.foodrisk.service;

import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AnalysisSessionCleanupServiceTest {

    @Autowired
    private AnalysisSessionCleanupService cleanupService;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    @Test
    @DisplayName("Scheduled cleanup should purge expired sessions and preserve active sessions")
    void testPurgeExpiredSessions() {
        Instant now = Instant.now();

        // 1. Create an expired session (expired 10 minutes ago)
        FoodAnalysisSession expiredSession = new FoodAnalysisSession();
        expiredSession.setSessionToken("token-expired-purge-test");
        expiredSession.setStatus(AnalysisStatus.CREATED);
        expiredSession.setExpiresAt(now.minus(10, ChronoUnit.MINUTES));
        expiredSession = sessionRepository.save(expiredSession);

        // 2. Create an active session (expires in 15 minutes)
        FoodAnalysisSession activeSession = new FoodAnalysisSession();
        activeSession.setSessionToken("token-active-purge-test");
        activeSession.setStatus(AnalysisStatus.CREATED);
        activeSession.setExpiresAt(now.plus(15, ChronoUnit.MINUTES));
        activeSession = sessionRepository.save(activeSession);

        // 3. Run purge
        int purged = cleanupService.purgeExpiredSessions(now);

        assertThat(purged).isGreaterThanOrEqualTo(1);

        // 4. Verify expired session is gone from database
        Optional<FoodAnalysisSession> foundExpired = sessionRepository.findById(expiredSession.getId());
        assertThat(foundExpired).isEmpty();

        // 5. Verify active session remains in database
        Optional<FoodAnalysisSession> foundActive = sessionRepository.findById(activeSession.getId());
        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().getId()).isEqualTo(activeSession.getId());
    }
}
