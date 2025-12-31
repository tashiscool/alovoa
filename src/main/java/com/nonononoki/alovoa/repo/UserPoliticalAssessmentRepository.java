package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.EconomicClass;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.GateStatus;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.PoliticalOrientation;
import com.nonononoki.alovoa.entity.user.UserPoliticalAssessment.VasectomyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPoliticalAssessmentRepository extends JpaRepository<UserPoliticalAssessment, Long> {

    Optional<UserPoliticalAssessment> findByUuid(UUID uuid);

    Optional<UserPoliticalAssessment> findByUser(User user);

    // Find by gate status (for admin review)
    List<UserPoliticalAssessment> findByGateStatus(GateStatus status);

    Page<UserPoliticalAssessment> findByGateStatus(GateStatus status, Pageable pageable);

    // Find pending explanations (working-class conservatives)
    List<UserPoliticalAssessment> findByGateStatusAndConservativeExplanationIsNotNull(GateStatus status);

    // Find pending vasectomy verifications
    List<UserPoliticalAssessment> findByVasectomyStatus(VasectomyStatus status);

    // Find by economic class
    List<UserPoliticalAssessment> findByEconomicClass(EconomicClass economicClass);

    // Find by political orientation
    List<UserPoliticalAssessment> findByPoliticalOrientation(PoliticalOrientation orientation);

    // Find users with high class consciousness scores
    List<UserPoliticalAssessment> findByClassConsciousnessScoreGreaterThanEqual(Double minScore);

    // Count users by gate status
    long countByGateStatus(GateStatus status);

    // Count rejected users
    @Query("SELECT COUNT(a) FROM UserPoliticalAssessment a WHERE a.gateStatus = 'REJECTED'")
    long countRejected();

    // Find approved users for matching
    @Query("SELECT a FROM UserPoliticalAssessment a WHERE a.gateStatus = 'APPROVED' " +
           "AND a.economicValuesScore >= :minScore")
    List<UserPoliticalAssessment> findApprovedWithMinValuesScore(@Param("minScore") Double minScore);

    // Find users by economic class and orientation (for stats)
    @Query("SELECT a.economicClass, a.politicalOrientation, COUNT(a) FROM UserPoliticalAssessment a " +
           "GROUP BY a.economicClass, a.politicalOrientation")
    List<Object[]> getClassOrientationDistribution();

    // Find compatible users (same or similar economic values)
    @Query("SELECT a FROM UserPoliticalAssessment a WHERE a.gateStatus = 'APPROVED' " +
           "AND ABS(a.economicValuesScore - :targetScore) <= :tolerance " +
           "AND a.user != :excludeUser")
    List<UserPoliticalAssessment> findCompatibleUsers(
        @Param("targetScore") Double targetScore,
        @Param("tolerance") Double tolerance,
        @Param("excludeUser") User excludeUser);

    // Find users needing re-evaluation (old assessment version)
    List<UserPoliticalAssessment> findByAssessmentVersionLessThan(Integer currentVersion);

    // Check if user needs vasectomy verification
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM UserPoliticalAssessment a " +
           "WHERE a.user = :user AND a.gateStatus = 'PENDING_VASECTOMY'")
    boolean needsVasectomyVerification(@Param("user") User user);
}
