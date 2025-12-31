package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.CompatibilityScore;
import com.nonononoki.alovoa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompatibilityScoreRepository extends JpaRepository<CompatibilityScore, Long> {
    Optional<CompatibilityScore> findByUserAAndUserB(User userA, User userB);

    @Query("SELECT c FROM CompatibilityScore c WHERE (c.userA = :user OR c.userB = :user) ORDER BY c.overallScore DESC")
    List<CompatibilityScore> findTopMatchesForUser(@Param("user") User user);

    @Query("SELECT c FROM CompatibilityScore c WHERE c.userA = :user ORDER BY c.overallScore DESC")
    List<CompatibilityScore> findByUserAOrderByOverallScoreDesc(@Param("user") User user);
}
