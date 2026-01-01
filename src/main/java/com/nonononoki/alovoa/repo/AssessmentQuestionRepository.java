package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.AssessmentQuestion.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {

    Optional<AssessmentQuestion> findByExternalId(String externalId);

    List<AssessmentQuestion> findByExternalIdIn(List<String> externalIds);

    List<AssessmentQuestion> findByCategoryAndActiveTrue(QuestionCategory category);

    List<AssessmentQuestion> findByCategoryAndSubcategoryAndActiveTrue(QuestionCategory category, String subcategory);

    List<AssessmentQuestion> findByDomainAndActiveTrue(String domain);

    List<AssessmentQuestion> findByActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT q FROM AssessmentQuestion q WHERE q.active = true AND q.category = :category ORDER BY q.displayOrder")
    List<AssessmentQuestion> findActiveQuestionsByCategory(@Param("category") QuestionCategory category);

    @Query("SELECT DISTINCT q.subcategory FROM AssessmentQuestion q WHERE q.category = :category AND q.subcategory IS NOT NULL")
    List<String> findDistinctSubcategoriesByCategory(@Param("category") QuestionCategory category);

    @Query("SELECT DISTINCT q.domain FROM AssessmentQuestion q WHERE q.category = 'BIG_FIVE' AND q.domain IS NOT NULL")
    List<String> findDistinctBigFiveDomains();

    long countByCategory(QuestionCategory category);

    long countByCategoryAndActiveTrue(QuestionCategory category);

    boolean existsByExternalId(String externalId);

    // Methods for intake flow with string-based categories (uses subcategory field)
    List<AssessmentQuestion> findBySubcategory(String subcategory);

    Optional<AssessmentQuestion> findBySubcategoryAndCoreQuestionTrue(String subcategory);

    List<AssessmentQuestion> findByCoreQuestionTrue();

    @Query("SELECT DISTINCT q.subcategory FROM AssessmentQuestion q WHERE q.coreQuestion = true")
    List<String> findCategoriesWithCoreQuestions();
}
