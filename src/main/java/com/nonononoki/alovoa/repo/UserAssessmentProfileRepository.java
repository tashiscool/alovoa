package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentProfile;
import com.nonononoki.alovoa.entity.UserAssessmentProfile.AttachmentStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAssessmentProfileRepository extends JpaRepository<UserAssessmentProfile, Long> {

    Optional<UserAssessmentProfile> findByUser(User user);

    List<UserAssessmentProfile> findByAttachmentStyle(AttachmentStyle style);

    List<UserAssessmentProfile> findByProfileCompleteTrue();

    @Query("SELECT p FROM UserAssessmentProfile p WHERE p.bigFiveComplete = true")
    List<UserAssessmentProfile> findWithCompleteBigFive();

    @Query("SELECT p FROM UserAssessmentProfile p WHERE " +
           "ABS(p.opennessScore - :openness) <= :tolerance AND " +
           "ABS(p.conscientiousnessScore - :conscientiousness) <= :tolerance AND " +
           "ABS(p.extraversionScore - :extraversion) <= :tolerance AND " +
           "ABS(p.agreeablenessScore - :agreeableness) <= :tolerance AND " +
           "ABS(p.emotionalStabilityScore - :emotionalStability) <= :tolerance")
    List<UserAssessmentProfile> findSimilarPersonalities(
            @Param("openness") Double openness,
            @Param("conscientiousness") Double conscientiousness,
            @Param("extraversion") Double extraversion,
            @Param("agreeableness") Double agreeableness,
            @Param("emotionalStability") Double emotionalStability,
            @Param("tolerance") Double tolerance);

    @Query("SELECT p FROM UserAssessmentProfile p WHERE p.user.id IN :userIds")
    List<UserAssessmentProfile> findByUserIds(@Param("userIds") List<Long> userIds);

    boolean existsByUser(User user);

    void deleteByUser(User user);
}
