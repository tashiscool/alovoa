package com.nonononoki.alovoa.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress;
import com.nonononoki.alovoa.entity.user.UserScaffoldingProgress.ScaffoldingStatus;

@Repository
public interface UserScaffoldingProgressRepository extends JpaRepository<UserScaffoldingProgress, Long> {

    Optional<UserScaffoldingProgress> findByUser(User user);

    Optional<UserScaffoldingProgress> findByUserId(Long userId);

    List<UserScaffoldingProgress> findByStatus(ScaffoldingStatus status);

    @Query("SELECT p FROM UserScaffoldingProgress p WHERE p.status = 'SEGMENTS_COMPLETE' AND p.inferenceGenerated = false")
    List<UserScaffoldingProgress> findReadyForInference();

    @Query("SELECT p FROM UserScaffoldingProgress p WHERE p.status = 'REVIEW_PENDING' AND p.inferenceReviewed = false")
    List<UserScaffoldingProgress> findPendingReview();

    @Query("SELECT COUNT(p) FROM UserScaffoldingProgress p WHERE p.status = 'CONFIRMED'")
    Long countConfirmed();

    @Query("SELECT p FROM UserScaffoldingProgress p WHERE p.user = :user AND p.inferenceConfirmed = true")
    Optional<UserScaffoldingProgress> findConfirmedByUser(@Param("user") User user);

    boolean existsByUserAndStatus(User user, ScaffoldingStatus status);

    void deleteByUser(User user);
}
