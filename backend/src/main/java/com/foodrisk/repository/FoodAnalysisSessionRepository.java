package com.foodrisk.repository;

import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for FoodAnalysisSession entity operations.
 *
 * Supports transient session lookup, status filtering, and expiration cleanup queries.
 */
@Repository
public interface FoodAnalysisSessionRepository extends JpaRepository<FoodAnalysisSession, UUID> {

    /**
     * Looks up an active session by its public session token.
     *
     * @param sessionToken unique session token
     * @return Optional containing session if found
     */
    Optional<FoodAnalysisSession> findBySessionToken(String sessionToken);

    /**
     * Looks up a session by ID with the associated User eagerly fetched.
     *
     * @param id session UUID
     * @return Optional containing session if found
     */
    @org.springframework.data.jpa.repository.Query("SELECT s FROM FoodAnalysisSession s LEFT JOIN FETCH s.user WHERE s.id = :id")
    Optional<FoodAnalysisSession> findByIdWithUser(@org.springframework.data.repository.query.Param("id") UUID id);

    /**
     * Finds all sessions that have an expiration timestamp before the specified instant.
     * Useful for scheduled eviction or cleanup of transient sessions.
     *
     * @param timestamp cutoff timestamp
     * @return list of expired sessions
     */
    List<FoodAnalysisSession> findByExpiresAtBefore(Instant timestamp);

    /**
     * Finds sessions by their current lifecycle status.
     *
     * @param status status filter
     * @return list of sessions matching status
     */
    List<FoodAnalysisSession> findByStatus(AnalysisStatus status);

    /**
     * Purges all expired sessions older than cutoff timestamp.
     *
     * @param cutoff timestamp cutoff
     * @return number of deleted records
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM FoodAnalysisSession s WHERE s.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
