package com.nonononoki.alovoa.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponseDto {

    private String questionId;
    private Integer numericResponse;
    private String textResponse;

    // === OKCupid-style matching fields ===

    /**
     * User's importance rating for this question.
     * Values: irrelevant, a_little, somewhat, very, mandatory
     */
    private String importance;

    /**
     * JSON array of acceptable answers from a partner.
     * For Likert 1-5: "[1,2]" means only 1 or 2 are acceptable.
     * For multiple choice: "[\"a\",\"b\"]" means options a or b acceptable.
     */
    private String acceptableAnswers;

    /**
     * Optional explanation for why this answer matters.
     * Shown to matches to provide context.
     */
    private String explanation;

    /**
     * Whether to show this Q&A on public profile.
     * Default: false (private, used only for matching).
     */
    private Boolean publicVisible;

    public AssessmentResponseDto(String questionId, Integer numericResponse) {
        this.questionId = questionId;
        this.numericResponse = numericResponse;
    }

    public AssessmentResponseDto(String questionId, String textResponse) {
        this.questionId = questionId;
        this.textResponse = textResponse;
    }

    /**
     * Convenience constructor for full OKCupid-style response.
     */
    public AssessmentResponseDto(String questionId, Integer numericResponse,
                                  String importance, String acceptableAnswers) {
        this.questionId = questionId;
        this.numericResponse = numericResponse;
        this.importance = importance;
        this.acceptableAnswers = acceptableAnswers;
    }
}
