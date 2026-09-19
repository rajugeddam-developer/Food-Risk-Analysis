package com.foodrisk.service;

import com.foodrisk.dto.AnalysisSessionResponse;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.entity.User;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.exception.SessionNotFoundException;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Service managing transient food analysis sessions.
 *
 * Enforces privacy guarantees:
 * - Sessions only store metadata and lifecycle timestamps (expiresAt).
 * - Zero storage of image payloads or OCR text in database tables.
 * - Enforces strict 15-minute TTL; expired sessions reject subsequent processing.
 */
@Service
public class AnalysisSessionService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FoodAnalysisSessionRepository sessionRepository;
    private final int sessionTtlMinutes;

    public AnalysisSessionService(
            FoodAnalysisSessionRepository sessionRepository,
            @Value("${analysis.session.ttl-minutes:15}") int sessionTtlMinutes
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    /**
     * Creates a new transient food analysis session.
     *
     * @param user optional authenticated user (null for guest scans)
     * @return created AnalysisSessionResponse with public session token and expiration time
     */
    @Transactional
    public AnalysisSessionResponse createSession(User user) {
        String token = generateSecureSessionToken();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(sessionTtlMinutes));

        FoodAnalysisSession session = new FoodAnalysisSession(token, AnalysisStatus.CREATED, user, expiresAt);
        FoodAnalysisSession saved = sessionRepository.save(session);

        log.info("Created analysis session {} (user: {}, expiresAt: {})",
                saved.getId(), user != null ? user.getId() : "guest", expiresAt);

        return toResponse(saved);
    }

    /**
     * Retrieves an active session, verifying expiration.
     *
     * @param sessionId UUID of session
     * @return active FoodAnalysisSession entity
     * @throws SessionNotFoundException if session does not exist
     * @throws SessionExpiredException if session is past expiresAt
     */
    @Transactional
    public FoodAnalysisSession getActiveSession(UUID sessionId) {
        FoodAnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Analysis session not found: " + sessionId));

        if (session.isExpired()) {
            if (session.getStatus() != AnalysisStatus.EXPIRED) {
                session.setStatus(AnalysisStatus.EXPIRED);
                sessionRepository.save(session);
            }
            log.warn("Analysis session {} has expired (expired at {})", sessionId, session.getExpiresAt());
            throw new SessionExpiredException("Analysis session " + sessionId + " has expired. Please initiate a new scan.");
        }

        return session;
    }

    /**
     * Validates that the active session can be accessed by the requesting authentication.
     * Enforces ownership for authenticated sessions:
     * - If session.getUser() != null: authentication must be non-null, authenticated, and match the user's email.
     * - If session.getUser() == null: guest session, allowed within TTL.
     *
     * @param sessionId UUID of session
     * @param authentication active Spring Security authentication (or null)
     * @return active FoodAnalysisSession
     * @throws org.springframework.security.access.AccessDeniedException if ownership check fails
     */
    @Transactional
    public FoodAnalysisSession validateSessionAccess(UUID sessionId, org.springframework.security.core.Authentication authentication) {
        FoodAnalysisSession session = getActiveSession(sessionId);
        validateSessionOwnership(session, authentication);
        return session;
    }

    public void validateSessionOwnership(FoodAnalysisSession session, org.springframework.security.core.Authentication authentication) {
        if (session == null || session.getUser() == null) {
            // Guest session: access permitted within 15-min TTL
            return;
        }

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required to access this analysis session.");
        }

        if (!session.getUser().getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to access this analysis session.");
        }
    }

    /**
     * Retrieves an active session and converts to response DTO.
     */
    @Transactional(readOnly = true)
    public AnalysisSessionResponse getSessionResponse(UUID sessionId) {
        FoodAnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Analysis session not found: " + sessionId));

        if (session.isExpired()) {
            throw new SessionExpiredException("Analysis session " + sessionId + " has expired. Please initiate a new scan.");
        }

        return toResponse(session);
    }

    @Transactional
    public void updateStatus(FoodAnalysisSession session, AnalysisStatus newStatus) {
        session.setStatus(newStatus);
        sessionRepository.save(session);
    }

    @Transactional
    public void updateStatus(UUID sessionId, AnalysisStatus newStatus) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(newStatus);
            sessionRepository.save(session);
        });
    }

    public AnalysisSessionResponse toResponse(FoodAnalysisSession session) {
        return new AnalysisSessionResponse(
                session.getId(),
                session.getSessionToken(),
                session.getStatus().name(),
                session.getExpiresAt(),
                session.getCreatedAt()
        );
    }

    private String generateSecureSessionToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
