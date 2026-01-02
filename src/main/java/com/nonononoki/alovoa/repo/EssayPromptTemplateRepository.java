package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.EssayPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EssayPromptTemplateRepository extends JpaRepository<EssayPromptTemplate, Long> {

    Optional<EssayPromptTemplate> findByPromptId(Long promptId);

    List<EssayPromptTemplate> findAllByOrderByDisplayOrderAsc();
}
