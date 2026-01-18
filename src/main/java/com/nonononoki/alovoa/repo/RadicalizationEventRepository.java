package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent;
import com.nonononoki.alovoa.entity.user.RadicalizationEvent.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RadicalizationEventRepository extends JpaRepository<RadicalizationEvent, Long> {

    Optional<RadicalizationEvent> findByUuid(UUID uuid);

    List<RadicalizationEvent> findByUser(User user);

    List<RadicalizationEvent> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Count events by tier for a user within a date range
     */
    long countByUserAndTierAndCreatedAtAfter(User user, Integer tier, Date after);

    /**
     * Find users with Tier 3 events who haven't received intervention
     */
    @Query("SELECT DISTINCT e.user FROM RadicalizationEvent e WHERE e.tier = 3 AND e.interventionSent = false")
    List<User> findUsersNeedingUrgentIntervention();

    /**
     * Find users with multiple Tier 2 events (pattern detection)
     */
    @Query("SELECT e.user, COUNT(e) as eventCount FROM RadicalizationEvent e " +
           "WHERE e.tier = 2 AND e.createdAt > :since " +
           "GROUP BY e.user HAVING COUNT(e) >= :threshold")
    List<Object[]> findUsersWithRepeatedTier2Events(@Param("since") Date since, @Param("threshold") long threshold);

    /**
     * Check if content hash already processed (deduplication)
     */
    boolean existsByUserAndContentHash(User user, String contentHash);

    /**
     * Get recent events for a user
     */
    List<RadicalizationEvent> findByUserAndCreatedAtAfterOrderByCreatedAtDesc(User user, Date after);

    /**
     * Count total events by tier (for analytics)
     */
    long countByTier(Integer tier);

    /**
     * Count events where resources were accessed (intervention effectiveness)
     */
    long countByInterventionSentAndResourcesAccessed(Boolean interventionSent, Boolean resourcesAccessed);
}
