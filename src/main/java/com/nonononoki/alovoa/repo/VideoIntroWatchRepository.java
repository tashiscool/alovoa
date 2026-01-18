package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.VideoIntroWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoIntroWatchRepository extends JpaRepository<VideoIntroWatch, Long> {

    /**
     * Find a specific watch record between viewer and profile owner
     */
    Optional<VideoIntroWatch> findByViewerAndProfileOwner(User viewer, User profileOwner);

    /**
     * Check if viewer has watched profile owner's video
     */
    boolean existsByViewerAndProfileOwner(User viewer, User profileOwner);

    /**
     * Check if viewer has completed watching profile owner's video
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM VideoIntroWatch w WHERE w.viewer = :viewer AND w.profileOwner = :owner AND w.completed = true")
    boolean hasCompletedWatching(@Param("viewer") User viewer, @Param("owner") User owner);

    /**
     * Get all profiles the viewer has watched
     */
    List<VideoIntroWatch> findByViewer(User viewer);

    /**
     * Get all users who have watched a profile owner's video
     */
    List<VideoIntroWatch> findByProfileOwner(User profileOwner);

    /**
     * Count how many times a profile's video has been watched
     */
    long countByProfileOwner(User profileOwner);

    /**
     * Count completed watches for a profile
     */
    long countByProfileOwnerAndCompletedTrue(User profileOwner);
}
