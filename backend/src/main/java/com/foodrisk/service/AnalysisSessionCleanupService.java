package com.foodrisk.service;

import com.foodrisk.repository.FoodAnalysisSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled background service for purging expired transient food analysis sessions.
 *
 * Enforces privacy and storage efficiency:
 * - Automatically purges sessions older than their TTL (expires_at).
 * - Active sessions are untouched.
 * - Non-blocking scheduled execution.
 */
@Service
public class AnalysisSessionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisSessionCleanupService.class);

    private final FoodAnalysisSessionRepository sessionRepository;

    public AnalysisSessionCleanupService(FoodAnalysisSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Scheduled cleanup running every 5 minutes (default 300,000 ms).
     */
    @Scheduled(fixedRateString = "${analysis.session.cleanup-interval-ms:300000}", initialDelay = 60000)
    @Transactional
    public void scheduleCleanup() {
        int purged = purgeExpiredSessions(Instant.now());
        if (purged > 0) {
            log.info("Scheduled cleanup successfully purged {} expired food analysis session(s).", purged);
        }
    }

    /**
     * Purges sessions expired prior to the specified cutoff instant.
     *
     * @param cutoff timestamp cutoff
     * @return count of deleted session records
     */
    @Transactional
    public int purgeExpiredSessions(Instant cutoff) {
        int deleted = sessionRepository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.debug("Purged {} expired session(s) prior to {}", deleted, cutoff);
        }
        return deleted;
    }
}
