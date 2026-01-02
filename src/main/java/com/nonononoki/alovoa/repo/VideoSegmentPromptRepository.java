package com.nonononoki.alovoa.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nonononoki.alovoa.entity.VideoSegmentPrompt;
import com.nonononoki.alovoa.entity.VideoSegmentPrompt.PromptCategory;

@Repository
public interface VideoSegmentPromptRepository extends JpaRepository<VideoSegmentPrompt, Long> {

    Optional<VideoSegmentPrompt> findByPromptKey(String promptKey);

    List<VideoSegmentPrompt> findByActiveOrderByDisplayOrderAsc(Boolean active);

    List<VideoSegmentPrompt> findByCategoryAndActiveOrderByDisplayOrderAsc(PromptCategory category, Boolean active);

    List<VideoSegmentPrompt> findByRequiredForMatchingAndActiveOrderByDisplayOrderAsc(Boolean required, Boolean active);

    @Query("SELECT p FROM VideoSegmentPrompt p WHERE p.active = true ORDER BY p.displayOrder ASC")
    List<VideoSegmentPrompt> findAllActiveOrdered();

    @Query("SELECT p FROM VideoSegmentPrompt p WHERE p.requiredForMatching = true AND p.active = true ORDER BY p.displayOrder ASC")
    List<VideoSegmentPrompt> findRequiredPrompts();

    @Query("SELECT COUNT(p) FROM VideoSegmentPrompt p WHERE p.requiredForMatching = true AND p.active = true")
    Integer countRequiredPrompts();
}
