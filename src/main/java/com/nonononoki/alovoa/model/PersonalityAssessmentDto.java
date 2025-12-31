package com.nonononoki.alovoa.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class PersonalityAssessmentDto {
    private Map<String, Integer> answers;
    private Integer version;
}
