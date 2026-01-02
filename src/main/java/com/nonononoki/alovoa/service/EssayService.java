package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.EssayPromptTemplate;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPrompt;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.EssayDto;
import com.nonononoki.alovoa.repo.EssayPromptTemplateRepository;
import com.nonononoki.alovoa.repo.UserPromptRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing user essay prompts (the 10 fixed OKCupid-style profile essays).
 */
@Service
public class EssayService {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPromptRepository userPromptRepository;

    @Autowired
    private EssayPromptTemplateRepository essayTemplateRepository;

    /**
     * Get all essay prompt templates.
     */
    public List<EssayPromptTemplate> getAllTemplates() {
        return essayTemplateRepository.findAllByOrderByDisplayOrderAsc();
    }

    /**
     * Get a user's essays with their corresponding templates.
     */
    public List<EssayDto> getUserEssays(User user) {
        List<EssayPromptTemplate> templates = getAllTemplates();

        // Get user's existing prompts indexed by promptId
        Map<Long, UserPrompt> userPromptMap = user.getPrompts().stream()
                .filter(p -> p.getPromptId() != null && p.getPromptId() >= 1 && p.getPromptId() <= 10)
                .collect(Collectors.toMap(UserPrompt::getPromptId, p -> p, (a, b) -> a));

        List<EssayDto> essays = new ArrayList<>();
        for (EssayPromptTemplate template : templates) {
            UserPrompt userPrompt = userPromptMap.get(template.getPromptId());
            essays.add(EssayDto.builder()
                    .promptId(template.getPromptId())
                    .title(template.getTitle())
                    .placeholder(template.getPlaceholder())
                    .helpText(template.getHelpText())
                    .displayOrder(template.getDisplayOrder())
                    .minLength(template.getMinLength())
                    .maxLength(template.getMaxLength())
                    .required(template.getRequired())
                    .answer(userPrompt != null ? userPrompt.getText() : null)
                    .build());
        }
        return essays;
    }

    /**
     * Get the current user's essays.
     */
    public List<EssayDto> getCurrentUserEssays() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return getUserEssays(user);
    }

    /**
     * Save or update a user's essay answer.
     */
    @Transactional
    public void saveEssay(Long promptId, String text) throws AlovoaException {
        if (promptId == null || promptId < 1 || promptId > 10) {
            throw new AlovoaException("invalid_prompt_id");
        }

        User user = authService.getCurrentUser(true);

        // Validate against template
        EssayPromptTemplate template = essayTemplateRepository.findByPromptId(promptId)
                .orElseThrow(() -> new AlovoaException("prompt_not_found"));

        // Validate length
        if (text != null) {
            if (template.getMinLength() != null && text.length() < template.getMinLength()) {
                throw new AlovoaException("essay_too_short");
            }
            if (template.getMaxLength() != null && text.length() > template.getMaxLength()) {
                throw new AlovoaException("essay_too_long");
            }
        }

        // Find existing prompt or create new one
        UserPrompt userPrompt = user.getPrompts().stream()
                .filter(p -> promptId.equals(p.getPromptId()))
                .findFirst()
                .orElse(null);

        if (text == null || text.isBlank()) {
            // Delete the essay if text is empty
            if (userPrompt != null) {
                user.getPrompts().remove(userPrompt);
                userPromptRepository.delete(userPrompt);
            }
        } else {
            if (userPrompt == null) {
                userPrompt = new UserPrompt();
                userPrompt.setUser(user);
                userPrompt.setPromptId(promptId);
                user.getPrompts().add(userPrompt);
            }
            userPrompt.setText(text.trim());
            userPromptRepository.save(userPrompt);
        }

        userRepository.saveAndFlush(user);
    }

    /**
     * Save multiple essays at once.
     */
    @Transactional
    public void saveEssays(Map<Long, String> essays) throws AlovoaException {
        for (Map.Entry<Long, String> entry : essays.entrySet()) {
            saveEssay(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get essay count for a user (for profile completeness).
     */
    public int getFilledEssayCount(User user) {
        return (int) user.getPrompts().stream()
                .filter(p -> p.getPromptId() != null && p.getPromptId() >= 1 && p.getPromptId() <= 10)
                .filter(p -> p.getText() != null && !p.getText().isBlank())
                .count();
    }

    /**
     * Get essay templates (prompts only, no user answers).
     */
    public List<EssayDto> getEssayTemplates() {
        List<EssayPromptTemplate> templates = getAllTemplates();
        return templates.stream()
                .map(template -> EssayDto.builder()
                        .promptId(template.getPromptId())
                        .title(template.getTitle())
                        .placeholder(template.getPlaceholder())
                        .helpText(template.getHelpText())
                        .displayOrder(template.getDisplayOrder())
                        .minLength(template.getMinLength())
                        .maxLength(template.getMaxLength())
                        .required(template.getRequired())
                        .answer(null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get filled essay count for the current user.
     */
    public long getFilledEssayCount() throws AlovoaException {
        User user = authService.getCurrentUser(true);
        return getFilledEssayCount(user);
    }
}
