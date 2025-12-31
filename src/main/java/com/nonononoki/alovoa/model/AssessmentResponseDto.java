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

    public AssessmentResponseDto(String questionId, Integer numericResponse) {
        this.questionId = questionId;
        this.numericResponse = numericResponse;
    }

    public AssessmentResponseDto(String questionId, String textResponse) {
        this.questionId = questionId;
        this.textResponse = textResponse;
    }
}
