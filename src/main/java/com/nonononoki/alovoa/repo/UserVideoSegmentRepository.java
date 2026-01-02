package com.nonononoki.alovoa.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoSegmentPrompt;
import com.nonononoki.alovoa.entity.user.UserVideoSegment;
import com.nonononoki.alovoa.entity.user.UserVideoSegment.AnalysisStatus;

@Repository
public interface UserVideoSegmentRepository extends JpaRepository<UserVideoSegment, Long> {

    Optional<UserVideoSegment> findByUuid(String uuid);

    List<UserVideoSegment> findByUser(User user);

    List<UserVideoSegment> findByUserOrderByPromptDisplayOrderAsc(User user);

    Optional<UserVideoSegment> findByUserAndPrompt(User user, VideoSegmentPrompt prompt);

    @Query("SELECT s FROM UserVideoSegment s WHERE s.user = :user AND s.prompt.promptKey = :promptKey")
    Optional<UserVideoSegment> findByUserAndPromptKey(@Param("user") User user, @Param("promptKey") String promptKey);

    List<UserVideoSegment> findByUserAndStatus(User user, AnalysisStatus status);

    @Query("SELECT s FROM UserVideoSegment s WHERE s.user = :user AND s.status = 'COMPLETED'")
    List<UserVideoSegment> findCompletedByUser(@Param("user") User user);

    @Query("SELECT COUNT(s) FROM UserVideoSegment s WHERE s.user = :user AND s.status = 'COMPLETED'")
    Integer countCompletedByUser(@Param("user") User user);

    @Query("SELECT COUNT(s) FROM UserVideoSegment s WHERE s.user = :user AND s.prompt.requiredForMatching = true AND s.status = 'COMPLETED'")
    Integer countCompletedRequiredByUser(@Param("user") User user);

    @Query("SELECT s FROM UserVideoSegment s WHERE s.status = 'PENDING' ORDER BY s.uploadedAt ASC")
    List<UserVideoSegment> findPendingAnalysis();

    void deleteByUser(User user);

    @Query("SELECT s FROM UserVideoSegment s JOIN FETCH s.prompt WHERE s.user.id = :userId ORDER BY s.prompt.displayOrder ASC")
    List<UserVideoSegment> findByUserIdWithPrompts(@Param("userId") Long userId);
}
