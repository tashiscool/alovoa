package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentResponse;
import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.AssessmentQuestion.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAssessmentResponseRepository extends JpaRepository<UserAssessmentResponse, Long> {

    List<UserAssessmentResponse> findByUser(User user);

    List<UserAssessmentResponse> findByUserAndCategory(User user, QuestionCategory category);

    Optional<UserAssessmentResponse> findByUserAndQuestion(User user, AssessmentQuestion question);

    @Query("SELECT r FROM UserAssessmentResponse r WHERE r.user = :user AND r.question.domain = :domain")
    List<UserAssessmentResponse> findByUserAndDomain(@Param("user") User user, @Param("domain") String domain);

    @Query("SELECT r FROM UserAssessmentResponse r WHERE r.user = :user AND r.question.externalId IN :questionIds")
    List<UserAssessmentResponse> findByUserAndQuestionExternalIds(@Param("user") User user, @Param("questionIds") List<String> questionIds);

    @Query("SELECT COUNT(r) FROM UserAssessmentResponse r WHERE r.user = :user AND r.category = :category")
    long countByUserAndCategory(@Param("user") User user, @Param("category") QuestionCategory category);

    @Query("SELECT AVG(r.numericResponse) FROM UserAssessmentResponse r WHERE r.user = :user AND r.question.domain = :domain AND r.question.keyed = 'plus'")
    Double averageScoreByUserAndDomainPlus(@Param("user") User user, @Param("domain") String domain);

    @Query("SELECT AVG(r.numericResponse) FROM UserAssessmentResponse r WHERE r.user = :user AND r.question.domain = :domain AND r.question.keyed = 'minus'")
    Double averageScoreByUserAndDomainMinus(@Param("user") User user, @Param("domain") String domain);

    @Query("SELECT AVG(r.numericResponse) FROM UserAssessmentResponse r WHERE r.user = :user AND r.question.dimension = :dimension")
    Double averageScoreByUserAndDimension(@Param("user") User user, @Param("dimension") String dimension);

    void deleteByUser(User user);

    void deleteByUserAndCategory(User user, QuestionCategory category);

    boolean existsByUserAndQuestion(User user, AssessmentQuestion question);
}
