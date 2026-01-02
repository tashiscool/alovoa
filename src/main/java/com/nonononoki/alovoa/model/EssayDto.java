package com.nonononoki.alovoa.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for essay prompt with user's answer.
 */
@Getter
@Setter
@Builder
public class EssayDto {
    private Long promptId;
    private String title;
    private String placeholder;
    private String helpText;
    private Integer displayOrder;
    private Integer minLength;
    private Integer maxLength;
    private Boolean required;
    private String answer;  // User's answer (null if not filled)
}
